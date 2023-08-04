package com.way.service;

import java.util.HashSet;
import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;

import com.way.activity.BaseActivity;
import com.way.activity.BaseActivity.BackPressHandler;
import com.way.activity.LoginActivity;
import com.way.activity.MainActivity;
import com.way.app.XXBroadcastReceiver;
import com.way.app.XXBroadcastReceiver.EventHandler;
import com.way.exception.XXException;
import com.way.smack.SmackImpl;
import com.way.util.L;
import com.way.util.NetUtil;
import com.way.util.PreferenceConstants;
import com.way.util.PreferenceUtils;
import com.way.util.T;
import com.way.xx.R;

public class XXService extends BaseService implements EventHandler,
		BackPressHandler {
	public static final int CONNECTED = 0;
	public static final int DISCONNECTED = -1;
	public static final int CONNECTING = 1;
	public static final String PONG_TIMEOUT = "pong timeout";// PONG_TIMEOUT
	public static final String NETWORK_ERROR = "network error";// network error
	public static final String LOGOUT = "logout";// LOGOUT
	public static final String LOGIN_FAILED = "login failed";// failure
	public static final String DISCONNECTED_WITHOUT_WARNING = "disconnected without warning";// 没有警告的断开连接

	private IBinder mBinder = new XXBinder();
	private IConnectionStatusCallback mConnectionStatusCallback;
	private SmackImpl mSmackable;
	private Thread mConnectingThread;
	private Handler mMainHandler = new Handler();

	private boolean mIsFirstLoginAction;
	//  start
	private static final int RECONNECT_AFTER = 5;
	private static final int RECONNECT_MAXIMUM = 10 * 60;// RECONNECT_MAXIMUM
	private static final String RECONNECT_ALARM = "com.way.xx.RECONNECT_ALARM";
	// private boolean mIsNeedReConnection = false;
	private int mConnectedState = DISCONNECTED; // isConnection
	private int mReconnectTimeout = RECONNECT_AFTER;
	private Intent mAlarmIntent = new Intent(RECONNECT_ALARM);
	private PendingIntent mPAlarmIntent;
	private BroadcastReceiver mAlarmReceiver = new ReconnectAlarmReceiver();
	//  end
	private ActivityManager mActivityManager;
	private String mPackageName;
	private HashSet<String> mIsBoundTo = new HashSet<String>();

	/**
	 * Callback for connection state changes when registering
	 * annotation surface and chat interface
	 * 
	 * @param cb
	 */
	public void registerConnectionStatusCallback(IConnectionStatusCallback cb) {
		mConnectionStatusCallback = cb;
	}

	public void unRegisterConnectionStatusCallback() {
		mConnectionStatusCallback = null;
	}

	@Override
	public IBinder onBind(Intent intent) {
		L.i(XXService.class, "[SERVICE] onBind");
		String chatPartner = intent.getDataString();
		if ((chatPartner != null)) {
			mIsBoundTo.add(chatPartner);
		}
		String action = intent.getAction();
		if (!TextUtils.isEmpty(action)
				&& TextUtils.equals(action, LoginActivity.LOGIN_ACTION)) {
			mIsFirstLoginAction = true;
		} else {
			mIsFirstLoginAction = false;
		}
		return mBinder;
	}

	@Override
	public void onRebind(Intent intent) {
		super.onRebind(intent);
		String chatPartner = intent.getDataString();
		if ((chatPartner != null)) {
			mIsBoundTo.add(chatPartner);
		}
		String action = intent.getAction();
		if (!TextUtils.isEmpty(action)
				&& TextUtils.equals(action, LoginActivity.LOGIN_ACTION)) {
			mIsFirstLoginAction = true;
		} else {
			mIsFirstLoginAction = false;
		}
	}

	@Override
	public boolean onUnbind(Intent intent) {
		String chatPartner = intent.getDataString();
		if ((chatPartner != null)) {
			mIsBoundTo.remove(chatPartner);
		}
		return true;
	}

	public class XXBinder extends Binder {
		public XXService getService() {
			return XXService.this;
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		XXBroadcastReceiver.mListeners.add(this);
		BaseActivity.mListeners.add(this);
		mActivityManager = ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE));
		mPackageName = getPackageName();
		mPAlarmIntent = PendingIntent.getBroadcast(this, 0, mAlarmIntent,
				PendingIntent.FLAG_UPDATE_CURRENT);
		registerReceiver(mAlarmReceiver, new IntentFilter(RECONNECT_ALARM));
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null
				&& intent.getAction() != null
				&& TextUtils.equals(intent.getAction(),
						XXBroadcastReceiver.BOOT_COMPLETED_ACTION)) {
			String account = PreferenceUtils.getPrefString(XXService.this,
					PreferenceConstants.ACCOUNT, "");
			String password = PreferenceUtils.getPrefString(XXService.this,
					PreferenceConstants.PASSWORD, "");
			if (!TextUtils.isEmpty(account) && !TextUtils.isEmpty(password))
				Login(account, password);
		}
		mMainHandler.removeCallbacks(monitorStatus);
		mMainHandler.postDelayed(monitorStatus, 1000L);
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		XXBroadcastReceiver.mListeners.remove(this);
		BaseActivity.mListeners.remove(this);
		((AlarmManager) getSystemService(Context.ALARM_SERVICE))
				.cancel(mPAlarmIntent);
		unregisterReceiver(mAlarmReceiver);// unregisterReceiver
		logout();
	}

	// login
	public void Login(final String account, final String password) {
		if (NetUtil.getNetworkState(this) == NetUtil.NETWORN_NONE) {
			connectionFailed(NETWORK_ERROR);
			return;
		}
		if (mConnectingThread != null) {
			L.i("a connection is still goign on!");
			return;
		}
		mConnectingThread = new Thread() {
			@Override
			public void run() {
				try {
					postConnecting();
					mSmackable = new SmackImpl(XXService.this);
					if (mSmackable.login(account, password)) {
						// success
						postConnectionScuessed();
					} else {
						// failure
						postConnectionFailed(LOGIN_FAILED);
					}
				} catch (XXException e) {
					String message = e.getLocalizedMessage();
					// failure
					if (e.getCause() != null)
						message += "\n" + e.getCause().getLocalizedMessage();
					postConnectionFailed(message);
					L.i(XXService.class, "YaximXMPPException in doConnect():");
					e.printStackTrace();
				} finally {
					if (mConnectingThread != null)
						synchronized (mConnectingThread) {
							mConnectingThread = null;
						}
				}
			}

		};
		mConnectingThread.start();
	}

	// exit
	public boolean logout() {
		boolean isLogout = false;
		if (mConnectingThread != null) {
			synchronized (mConnectingThread) {
				try {
					mConnectingThread.interrupt();
					mConnectingThread.join(50);
				} catch (InterruptedException e) {
					L.e("doDisconnect: failed catching connecting thread");
				} finally {
					mConnectingThread = null;
				}
			}
		}
		if (mSmackable != null) {
			isLogout = mSmackable.logout();
			mSmackable = null;
		}
		connectionFailed(LOGOUT);// exit
		return isLogout;
	}

	// sendMessage
	public void sendMessage(String user, String message) {
		if (mSmackable != null)
			mSmackable.sendMessage(user, message);
		else
			SmackImpl.sendOfflineMessage(getContentResolver(), user, message);
	}

	// isAuthenticated
	public boolean isAuthenticated() {
		if (mSmackable != null) {
			return mSmackable.isAuthenticated();
		}

		return false;
	}

	// clearNotifications
	public void clearNotifications(String Jid) {
		clearNotification(Jid);
	}

	/**
	 * Non-ui thread connection failure feedback
	 * 
	 * @param reason
	 */
	public void postConnectionFailed(final String reason) {
		mMainHandler.post(new Runnable() {
			public void run() {
				connectionFailed(reason);
			}
		});
	}

	// setting connection status
	public void setStatusFromConfig() {
		mSmackable.setStatusFromConfig();
	}

	// addRosterItem
	public void addRosterItem(String user, String alias, String group) {
		try {
			mSmackable.addRosterItem(user, alias, group);
		} catch (XXException e) {
			T.showShort(this, e.getMessage());
			L.e("exception in addRosterItem(): " + e.getMessage());
		}
	}

	// addGroup
	public void addRosterGroup(String group) {
		mSmackable.addRosterGroup(group);
	}

	// removeItem
	public void removeRosterItem(String user) {
		try {
			mSmackable.removeRosterItem(user);
		} catch (XXException e) {
			T.showShort(this, e.getMessage());
			L.e("exception in removeRosterItem(): " + e.getMessage());
		}
	}

	// moveItemToGroup
	public void moveRosterItemToGroup(String user, String group) {
		try {
			mSmackable.moveRosterItemToGroup(user, group);
		} catch (XXException e) {
			T.showShort(this, e.getMessage());
		}
	}

	// renameItem
	public void renameRosterItem(String user, String newName) {
		try {
			mSmackable.renameRosterItem(user, newName);
		} catch (XXException e) {
			T.showShort(this, e.getMessage());
			L.e("exception in renameRosterItem(): " + e.getMessage());
		}
	}

	// renameRosterGroup
	public void renameRosterGroup(String group, String newGroup) {
		mSmackable.renameRosterGroup(group, newGroup);
	}

	/**
	 * The UI thread feedback connection failed
	 * 
	 * @param reason
	 */
	private void connectionFailed(String reason) {
		L.i(XXService.class, "connectionFailed: " + reason);
		mConnectedState = DISCONNECTED;// update connection status
		if (TextUtils.equals(reason, LOGOUT)) {// exit
			((AlarmManager) getSystemService(Context.ALARM_SERVICE))
					.cancel(mPAlarmIntent);
			return;
		}
		// feedback
		if (mConnectionStatusCallback != null) {
			mConnectionStatusCallback.connectionStatusChanged(mConnectedState,
					reason);
			if (mIsFirstLoginAction)
				return;
		}

		// no connection
		if (NetUtil.getNetworkState(this) == NetUtil.NETWORN_NONE) {
			((AlarmManager) getSystemService(Context.ALARM_SERVICE))
					.cancel(mPAlarmIntent);
			return;
		}

		String account = PreferenceUtils.getPrefString(XXService.this,
				PreferenceConstants.ACCOUNT, "");
		String password = PreferenceUtils.getPrefString(XXService.this,
				PreferenceConstants.PASSWORD, "");
		// no save password
		if (TextUtils.isEmpty(account) || TextUtils.isEmpty(password)) {
			L.d("account = null || password = null");
			return;
		}
		// If you do not manually exit
		if (PreferenceUtils.getPrefBoolean(this,
				PreferenceConstants.AUTO_RECONNECT, true)) {
			L.d("connectionFailed(): registering reconnect in "
					+ mReconnectTimeout + "s");
			((AlarmManager) getSystemService(Context.ALARM_SERVICE)).set(
					AlarmManager.RTC_WAKEUP, System.currentTimeMillis()
							+ mReconnectTimeout * 1000, mPAlarmIntent);
			mReconnectTimeout = mReconnectTimeout * 2;
			if (mReconnectTimeout > RECONNECT_MAXIMUM)
				mReconnectTimeout = RECONNECT_MAXIMUM;
		} else {
			((AlarmManager) getSystemService(Context.ALARM_SERVICE))
					.cancel(mPAlarmIntent);
		}

	}

	private void postConnectionScuessed() {
		mMainHandler.post(new Runnable() {
			public void run() {
				connectionScuessed();
			}

		});
	}

	private void connectionScuessed() {
		mConnectedState = CONNECTED;// It's already connected
		mReconnectTimeout = RECONNECT_AFTER;// Reset the reconnection time

		if (mConnectionStatusCallback != null)
			mConnectionStatusCallback.connectionStatusChanged(mConnectedState,
					"");
	}

	// Tell the interface thread to do some processing
	private void postConnecting() {
		// TODO Auto-generated method stub
		mMainHandler.post(new Runnable() {
			public void run() {
				connecting();
			}
		});
	}

	private void connecting() {
		// TODO Auto-generated method stub
		mConnectedState = CONNECTING;// 连接中
		if (mConnectionStatusCallback != null)
			mConnectionStatusCallback.connectionStatusChanged(mConnectedState,
					"");
	}

	// New message received
	public void newMessage(final String from, final String message) {
		mMainHandler.post(new Runnable() {
			public void run() {
				if (!PreferenceUtils.getPrefBoolean(XXService.this,
						PreferenceConstants.SCLIENTNOTIFY, false))
					MediaPlayer.create(XXService.this, R.raw.office).start();
				if (!isAppOnForeground())
					notifyClient(from, mSmackable.getNameForJID(from), message,
							!mIsBoundTo.contains(from));

			}

		});
	}

	// rosterChanged
	public void rosterChanged() {
		// gracefully handle^W ignore events after a disconnect
		if (mSmackable == null)
			return;
		if (mSmackable != null && !mSmackable.isAuthenticated()) {
			L.i("rosterChanged(): disconnected without warning");
			connectionFailed(DISCONNECTED_WITHOUT_WARNING);
		}
	}

	/**
	 * updateServiceNotification
	 * 
	 * @param message
	 */
	public void updateServiceNotification(String message) {
		if (!PreferenceUtils.getPrefBoolean(this,
				PreferenceConstants.FOREGROUND, true))
			return;
		String title = PreferenceUtils.getPrefString(this,
				PreferenceConstants.ACCOUNT, "");
		Notification n = new Notification(R.drawable.login_default_avatar,
				title, System.currentTimeMillis());
		n.flags = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;

		Intent notificationIntent = new Intent(this, MainActivity.class);
		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		n.contentIntent = PendingIntent.getActivity(this, 0,
				notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		/*n.setLatestEventInfo(this, title, message, n.contentIntent);*/
		startForeground(SERVICE_NOTIFICATION, n);
	}

	// monitorStatus
	Runnable monitorStatus = new Runnable() {
		public void run() {
			try {
				L.i("monitorStatus is running... " + mPackageName);
				mMainHandler.removeCallbacks(monitorStatus);
				// isAppOnForeground
				if (!isAppOnForeground()) {
					L.i("app run in background...");
					// if (isAuthenticated())
					updateServiceNotification(getString(R.string.run_bg_ticker));
					return;
				} else {
					stopForeground(true);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};

	public boolean isAppOnForeground() {
		List<RunningTaskInfo> taskInfos = mActivityManager.getRunningTasks(1);
		if (taskInfos.size() > 0
				&& TextUtils.equals(getPackageName(),
						taskInfos.get(0).topActivity.getPackageName())) {
			return true;
		}
		return false;
	}

	// ReconnectAlarmReceiver
	private class ReconnectAlarmReceiver extends BroadcastReceiver {
		public void onReceive(Context ctx, Intent i) {
			L.d("Alarm received.");
			if (!PreferenceUtils.getPrefBoolean(XXService.this,
					PreferenceConstants.AUTO_RECONNECT, true)) {
				return;
			}
			if (mConnectedState != DISCONNECTED) {
				L.d("Reconnect attempt aborted: we are connected again!");
				return;
			}
			String account = PreferenceUtils.getPrefString(XXService.this,
					PreferenceConstants.ACCOUNT, "");
			String password = PreferenceUtils.getPrefString(XXService.this,
					PreferenceConstants.PASSWORD, "");
			if (TextUtils.isEmpty(account) || TextUtils.isEmpty(password)) {
				L.d("account = null || password = null");
				return;
			}
			Login(account, password);
		}
	}

	@Override
	public void onNetChange() {
		if (NetUtil.getNetworkState(this) == NetUtil.NETWORN_NONE) {// 如果是网络断开，不作处理
			connectionFailed(NETWORK_ERROR);
			return;
		}
		if (isAuthenticated())
			return;
		String account = PreferenceUtils.getPrefString(XXService.this,
				PreferenceConstants.ACCOUNT, "");
		String password = PreferenceUtils.getPrefString(XXService.this,
				PreferenceConstants.PASSWORD, "");
		if (TextUtils.isEmpty(account) || TextUtils.isEmpty(password))
			return;
		if (!PreferenceUtils.getPrefBoolean(this,
				PreferenceConstants.AUTO_RECONNECT, true))
			return;
		Login(account, password);
	}

	@Override
	public void activityOnResume() {
		L.i("activity onResume ...");
		mMainHandler.post(monitorStatus);
	}

	@Override
	public void activityOnPause() {
		L.i("activity onPause ...");
		mMainHandler.postDelayed(monitorStatus, 1000L);
	}
}
