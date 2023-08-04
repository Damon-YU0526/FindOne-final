package com.way.activity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.way.adapter.RosterAdapter;
import com.way.app.XXBroadcastReceiver;
import com.way.app.XXBroadcastReceiver.EventHandler;
import com.way.db.ChatProvider;
import com.way.db.RosterProvider;
import com.way.db.RosterProvider.RosterConstants;
import com.way.fragment.RecentChatFragment;
import com.way.fragment.SettingsFragment;
import com.way.iphonetreeview.IphoneTreeView;
import com.way.pulltorefresh.PullToRefreshBase;
import com.way.pulltorefresh.PullToRefreshBase.OnRefreshListener;
import com.way.pulltorefresh.PullToRefreshScrollView;
import com.way.quickaction.ActionItem;
import com.way.quickaction.QuickAction;
import com.way.quickaction.QuickAction.OnActionItemClickListener;
import com.way.service.IConnectionStatusCallback;
import com.way.service.XXService;
import com.way.slidingmenu.BaseSlidingFragmentActivity;
import com.way.slidingmenu.SlidingMenu;
import com.way.util.L;
import com.way.util.NetUtil;
import com.way.util.PreferenceConstants;
import com.way.util.PreferenceUtils;
import com.way.util.StatusMode;
import com.way.util.T;
import com.way.util.XMPPHelper;
import com.way.view.AddRosterItemDialog;
import com.way.view.CustomDialog;
import com.way.view.GroupNameView;
import com.way.xx.R;

public class MainActivity extends BaseSlidingFragmentActivity implements
		OnClickListener, IConnectionStatusCallback, EventHandler,
		FragmentCallBack {
	private static final int ID_CHAT = 0;
	private static final int ID_AVAILABLE = 1;
	private static final int ID_AWAY = 2;
	private static final int ID_XA = 3;
	private static final int ID_DND = 4;
	public static HashMap<String, Integer> mStatusMap;
	static {
		mStatusMap = new HashMap<String, Integer>();
		mStatusMap.put(PreferenceConstants.OFFLINE, -1);
		mStatusMap.put(PreferenceConstants.DND, R.drawable.status_shield);
		mStatusMap.put(PreferenceConstants.XA, R.drawable.status_invisible);
		mStatusMap.put(PreferenceConstants.AWAY, R.drawable.status_leave);
		mStatusMap.put(PreferenceConstants.AVAILABLE, R.drawable.status_online);
		mStatusMap.put(PreferenceConstants.CHAT, R.drawable.status_qme);
	}
	private Handler mainHandler = new Handler();
	private XXService mXxService;
	private SlidingMenu mSlidingMenu;
	private View mNetErrorView;
	private TextView mTitleNameView;
	private ImageView mTitleStatusView;
	private ProgressBar mTitleProgressBar;
	private PullToRefreshScrollView mPullRefreshScrollView;
	private IphoneTreeView mIphoneTreeView;
	private RosterAdapter mRosterAdapter;
	private ContentObserver mRosterObserver = new RosterObserver();
	private int mLongPressGroupId, mLongPressChildId;

	ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mXxService = ((XXService.XXBinder) service).getService();
			mXxService.registerConnectionStatusCallback(MainActivity.this);
			// connection server
			if (!mXxService.isAuthenticated()) {
				String usr = PreferenceUtils.getPrefString(MainActivity.this,
						PreferenceConstants.ACCOUNT, "");
				String password = PreferenceUtils.getPrefString(
						MainActivity.this, PreferenceConstants.PASSWORD, "");
				mXxService.Login(usr, password);

			} else {
				mTitleNameView.setText(XMPPHelper
						.splitJidAndServer(PreferenceUtils.getPrefString(
								MainActivity.this, PreferenceConstants.ACCOUNT,
								"")));
				setStatusImage(true);
				mTitleProgressBar.setVisibility(View.GONE);
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mXxService.unRegisterConnectionStatusCallback();
			mXxService = null;
		}

	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		startService(new Intent(MainActivity.this, XXService.class));

		initSlidingMenu();
		setContentView(R.layout.main_center_layout);
		initViews();
		registerListAdapter();
	}

	/**
	 * Press the back key twice in a row to exit
	 */
	private long firstTime;

	@Override
	public void onBackPressed() {
		if (System.currentTimeMillis() - firstTime < 3000) {
			finish();
		} else {
			firstTime = System.currentTimeMillis();
				T.showShort(this, R.string.press_again_backrun);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		bindXMPPService();
		getContentResolver().registerContentObserver(
				RosterProvider.CONTENT_URI, true, mRosterObserver);
		setStatusImage(isConnected());
		mRosterAdapter.requery();
		XXBroadcastReceiver.mListeners.add(this);
		if (NetUtil.getNetworkState(this) == NetUtil.NETWORN_NONE)
			mNetErrorView.setVisibility(View.VISIBLE);
		else
			mNetErrorView.setVisibility(View.GONE);
	}

	@Override
	protected void onPause() {
		super.onPause();
		getContentResolver().unregisterContentObserver(mRosterObserver);
		unbindXMPPService();
		XXBroadcastReceiver.mListeners.remove(this);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

	}

	private void registerListAdapter() {
		mRosterAdapter = new RosterAdapter(this, mIphoneTreeView,
				mPullRefreshScrollView);
		mIphoneTreeView.setAdapter(mRosterAdapter);
		mRosterAdapter.requery();
	}

	private void unbindXMPPService() {
		try {
			unbindService(mServiceConnection);
			L.i(LoginActivity.class, "[SERVICE] Unbind");
		} catch (IllegalArgumentException e) {
			L.e(LoginActivity.class, "Service wasn't bound!");
		}
	}

	private void bindXMPPService() {
		L.i(LoginActivity.class, "[SERVICE] Unbind");
		bindService(new Intent(MainActivity.this, XXService.class),
				mServiceConnection, Context.BIND_AUTO_CREATE
						+ Context.BIND_DEBUG_UNBIND);
	}

	private void initViews() {
		mNetErrorView = findViewById(R.id.net_status_bar_top);
		mSlidingMenu.setSecondaryMenu(R.layout.main_right_layout);
		FragmentTransaction mFragementTransaction = getSupportFragmentManager()
				.beginTransaction();
		Fragment mFrag = new SettingsFragment();
		mFragementTransaction.replace(R.id.main_right_fragment, mFrag);
		mFragementTransaction.commit();

		ImageButton mLeftBtn = ((ImageButton) findViewById(R.id.show_left_fragment_btn));
		mLeftBtn.setVisibility(View.VISIBLE);
		mLeftBtn.setOnClickListener(this);
		ImageButton mRightBtn = ((ImageButton) findViewById(R.id.show_right_fragment_btn));
		mRightBtn.setVisibility(View.VISIBLE);
		mRightBtn.setOnClickListener(this);
		mTitleNameView = (TextView) findViewById(R.id.ivTitleName);
		mTitleProgressBar = (ProgressBar) findViewById(R.id.ivTitleProgress);
		mTitleStatusView = (ImageView) findViewById(R.id.ivTitleStatus);
		mTitleNameView.setText(XMPPHelper.splitJidAndServer(PreferenceUtils
				.getPrefString(this, PreferenceConstants.ACCOUNT, "")));
		mTitleNameView.setOnClickListener(this);

		mPullRefreshScrollView = (PullToRefreshScrollView) findViewById(R.id.pull_refresh_scrollview);

		mPullRefreshScrollView
				.setOnRefreshListener(new OnRefreshListener<ScrollView>() {

					@Override
					public void onRefresh(
							PullToRefreshBase<ScrollView> refreshView) {
						new GetDataTask().execute();
					}
				});
		mIphoneTreeView = (IphoneTreeView) findViewById(R.id.iphone_tree_view);
		mIphoneTreeView.setHeaderView(getLayoutInflater().inflate(
				R.layout.contact_buddy_list_group, mIphoneTreeView, false));
		mIphoneTreeView.setEmptyView(findViewById(R.id.empty));
		mIphoneTreeView
				.setOnItemLongClickListener(new OnItemLongClickListener() {

					@Override
					public boolean onItemLongClick(AdapterView<?> parent,
							View view, int position, long id) {
						int groupPos = (Integer) view.getTag(R.id.xxx01);
						int childPos = (Integer) view.getTag(R.id.xxx02);
						mLongPressGroupId = groupPos;
						mLongPressChildId = childPos;
						if (childPos == -1) {
							showGroupQuickActionBar(view
									.findViewById(R.id.group_name));

						} else {

							showChildQuickActionBar(view
									.findViewById(R.id.icon));

						}
						return false;
					}
				});
		mIphoneTreeView.setOnChildClickListener(new OnChildClickListener() {

			@Override
			public boolean onChildClick(ExpandableListView parent, View v,
					int groupPosition, int childPosition, long id) {
				String userJid = mRosterAdapter.getChild(groupPosition,
						childPosition).getJid();
				String userName = mRosterAdapter.getChild(groupPosition,
						childPosition).getAlias();
				startChatActivity(userJid, userName);
				return false;
			}
		});
	}

	private void startChatActivity(String userJid, String userName) {
		Intent chatIntent = new Intent(MainActivity.this, ChatActivity.class);
		Uri userNameUri = Uri.parse(userJid);
		chatIntent.setData(userNameUri);
		chatIntent.putExtra(ChatActivity.INTENT_EXTRA_USERNAME, userName);
		startActivity(chatIntent);
	}

	private boolean isConnected() {
		return mXxService != null && mXxService.isAuthenticated();
	}

	private void showGroupQuickActionBar(View view) {
		QuickAction quickAction = new QuickAction(this, QuickAction.HORIZONTAL);
		quickAction
				.addActionItem(new ActionItem(0, getString(R.string.rename)));
		quickAction.addActionItem(new ActionItem(1,
				getString(R.string.add_friend)));
		quickAction
				.setOnActionItemClickListener(new OnActionItemClickListener() {

					@Override
					public void onItemClick(QuickAction source, int pos,
							int actionId) {
						// return
						if (!isConnected()) {
							T.showShort(MainActivity.this,
									R.string.conversation_net_error_label);
							return;
						}
						switch (actionId) {
						case 0:
							String groupName = mRosterAdapter.getGroup(
									mLongPressGroupId).getGroupName();
							if (TextUtils.isEmpty(groupName)) {// The default group cannot be renamed
								T.showShort(MainActivity.this,
										R.string.roster_group_rename_failed);
								return;
							}
							renameRosterGroupDialog(mRosterAdapter.getGroup(
									mLongPressGroupId).getGroupName());
							break;
						case 1:

							new AddRosterItemDialog(MainActivity.this,
									mXxService).show();// Adding Contacts
							break;
						default:
							break;
						}
					}
				});
		quickAction.show(view);
		quickAction.setAnimStyle(QuickAction.ANIM_GROW_FROM_CENTER);
	}

	private void showChildQuickActionBar(View view) {
		QuickAction quickAction = new QuickAction(this, QuickAction.HORIZONTAL);
		quickAction.addActionItem(new ActionItem(0, getString(R.string.open)));
		quickAction
				.addActionItem(new ActionItem(1, getString(R.string.rename)));
		quickAction.addActionItem(new ActionItem(2, getString(R.string.move)));
		quickAction
				.addActionItem(new ActionItem(3, getString(R.string.delete)));
		quickAction
				.setOnActionItemClickListener(new OnActionItemClickListener() {

					@Override
					public void onItemClick(QuickAction source, int pos,
							int actionId) {
						String userJid = mRosterAdapter.getChild(
								mLongPressGroupId, mLongPressChildId).getJid();
						String userName = mRosterAdapter.getChild(
								mLongPressGroupId, mLongPressChildId)
								.getAlias();
						switch (actionId) {
						case 0:
							startChatActivity(userJid, userName);
							break;
						case 1:
							if (!isConnected()) {
								T.showShort(MainActivity.this,
										R.string.conversation_net_error_label);
								break;
							}
							renameRosterItemDialog(userJid, userName);
							break;
						case 2:
							if (!isConnected()) {
								T.showShort(MainActivity.this,
										R.string.conversation_net_error_label);
								break;
							}
							moveRosterItemToGroupDialog(userJid);
							break;
						case 3:
							if (!isConnected()) {
								T.showShort(MainActivity.this,
										R.string.conversation_net_error_label);
								break;
							}
							removeRosterItemDialog(userJid, userName);
							break;
						default:
							break;
						}
					}
				});
		quickAction.show(view);
	}

	private void initSlidingMenu() {
		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(dm);
		int mScreenWidth = dm.widthPixels;// Gets the screen resolution width
		setBehindContentView(R.layout.main_left_layout);// Set Left menu
		FragmentTransaction mFragementTransaction = getSupportFragmentManager()
				.beginTransaction();
		Fragment mFrag = new RecentChatFragment();
		mFragementTransaction.replace(R.id.main_left_fragment, mFrag);
		mFragementTransaction.commit();
		// customize the SlidingMenu
		mSlidingMenu = getSlidingMenu();
		mSlidingMenu.setMode(SlidingMenu.LEFT_RIGHT);
		mSlidingMenu.setShadowWidth(mScreenWidth / 40);
		mSlidingMenu.setBehindOffset(mScreenWidth / 8);
		mSlidingMenu.setFadeDegree(0.35f);
		mSlidingMenu.setTouchModeAbove(SlidingMenu.TOUCHMODE_MARGIN);
		mSlidingMenu.setShadowDrawable(R.drawable.shadow_left);
		mSlidingMenu.setSecondaryShadowDrawable(R.drawable.shadow_right);
		mSlidingMenu.setFadeEnabled(true);
		mSlidingMenu.setBehindScrollScale(0.333f);
	}

	private static final String[] GROUPS_QUERY = new String[] {
			RosterConstants._ID, RosterConstants.GROUP, };
	private static final String[] ROSTER_QUERY = new String[] {
			RosterConstants._ID, RosterConstants.JID, RosterConstants.ALIAS,
			RosterConstants.STATUS_MODE, RosterConstants.STATUS_MESSAGE, };

	public List<String> getRosterGroups() {
		// we want all, online and offline
		List<String> list = new ArrayList<String>();
		Cursor cursor = getContentResolver().query(RosterProvider.GROUPS_URI,
				GROUPS_QUERY, null, null, RosterConstants.GROUP);
		int idx = cursor.getColumnIndex(RosterConstants.GROUP);
		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			list.add(cursor.getString(idx));
			cursor.moveToNext();
		}
		cursor.close();
		return list;
	}

	public List<String[]> getRosterContacts() {
		// we want all, online and offline
		List<String[]> list = new ArrayList<String[]>();
		Cursor cursor = getContentResolver().query(RosterProvider.CONTENT_URI,
				ROSTER_QUERY, null, null, RosterConstants.ALIAS);
		int JIDIdx = cursor.getColumnIndex(RosterConstants.JID);
		int aliasIdx = cursor.getColumnIndex(RosterConstants.ALIAS);
		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			String jid = cursor.getString(JIDIdx);
			String alias = cursor.getString(aliasIdx);
			if ((alias == null) || (alias.length() == 0))
				alias = jid;
			list.add(new String[] { jid, alias });
			cursor.moveToNext();
		}
		cursor.close();
		return list;
	}

	protected void setViewImage(ImageView v, String value) {
		int presenceMode = Integer.parseInt(value);
		int statusDrawable = getIconForPresenceMode(presenceMode);
		v.setImageResource(statusDrawable);
		if (statusDrawable == R.drawable.status_busy)
			v.setVisibility(View.INVISIBLE);

	}

	private int getIconForPresenceMode(int presenceMode) {
		return StatusMode.values()[presenceMode].getDrawableId();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.show_left_fragment_btn:
			mSlidingMenu.showMenu(true);
			break;
		case R.id.show_right_fragment_btn:
			mSlidingMenu.showSecondaryMenu(true);
			break;
		case R.id.ivTitleName:
			if (isConnected())
				showStatusQuickAction(v);
			break;
		default:
			break;
		}
	}

	private void showStatusQuickAction(View v) {
		QuickAction quickAction = new QuickAction(this, QuickAction.VERTICAL);
		quickAction.addActionItem(new ActionItem(ID_CHAT,
				getString(R.string.status_chat), getResources().getDrawable(
						R.drawable.status_qme)));
		quickAction.addActionItem(new ActionItem(ID_AVAILABLE,
				getString(R.string.status_available), getResources()
						.getDrawable(R.drawable.status_online)));
		quickAction.addActionItem(new ActionItem(ID_AWAY,
				getString(R.string.status_away), getResources().getDrawable(
						R.drawable.status_leave)));
		quickAction.addActionItem(new ActionItem(ID_XA,
				getString(R.string.status_xa), getResources().getDrawable(
						R.drawable.status_invisible)));
		quickAction.addActionItem(new ActionItem(ID_DND,
				getString(R.string.status_dnd), getResources().getDrawable(
						R.drawable.status_shield)));
		quickAction
				.setOnActionItemClickListener(new OnActionItemClickListener() {

					@Override
					public void onItemClick(QuickAction source, int pos,
							int actionId) {
						// TODO Auto-generated method stub
						if (!isConnected()) {
							T.showShort(MainActivity.this,
									R.string.conversation_net_error_label);
							return;
						}
						switch (actionId) {
						case ID_CHAT:
							mTitleStatusView
									.setImageResource(R.drawable.status_qme);
							PreferenceUtils.setPrefString(MainActivity.this,
									PreferenceConstants.STATUS_MODE,
									PreferenceConstants.CHAT);
							PreferenceUtils.setPrefString(MainActivity.this,
									PreferenceConstants.STATUS_MESSAGE,
									getString(R.string.status_chat));
							break;
						case ID_AVAILABLE:
							mTitleStatusView
									.setImageResource(R.drawable.status_online);
							PreferenceUtils.setPrefString(MainActivity.this,
									PreferenceConstants.STATUS_MODE,
									PreferenceConstants.AVAILABLE);
							PreferenceUtils.setPrefString(MainActivity.this,
									PreferenceConstants.STATUS_MESSAGE,
									getString(R.string.status_available));
							break;
						case ID_AWAY:
							mTitleStatusView
									.setImageResource(R.drawable.status_leave);
							PreferenceUtils.setPrefString(MainActivity.this,
									PreferenceConstants.STATUS_MODE,
									PreferenceConstants.AWAY);
							PreferenceUtils.setPrefString(MainActivity.this,
									PreferenceConstants.STATUS_MESSAGE,
									getString(R.string.status_away));
							break;
						case ID_XA:
							mTitleStatusView
									.setImageResource(R.drawable.status_invisible);
							PreferenceUtils.setPrefString(MainActivity.this,
									PreferenceConstants.STATUS_MODE,
									PreferenceConstants.XA);
							PreferenceUtils.setPrefString(MainActivity.this,
									PreferenceConstants.STATUS_MESSAGE,
									getString(R.string.status_xa));
							break;
						case ID_DND:
							mTitleStatusView
									.setImageResource(R.drawable.status_shield);
							PreferenceUtils.setPrefString(MainActivity.this,
									PreferenceConstants.STATUS_MODE,
									PreferenceConstants.DND);
							PreferenceUtils.setPrefString(MainActivity.this,
									PreferenceConstants.STATUS_MESSAGE,
									getString(R.string.status_dnd));
							break;
						default:
							break;
						}
						mXxService.setStatusFromConfig();
						SettingsFragment fragment = (SettingsFragment) getSupportFragmentManager()
								.findFragmentById(R.id.main_right_fragment);
						fragment.readData();
					}
				});
		quickAction.show(v);
	}

	public abstract class EditOk {
		abstract public void ok(String result);
	}

	void removeChatHistory(final String JID) {
		getContentResolver().delete(ChatProvider.CONTENT_URI,
				ChatProvider.ChatConstants.JID + " = ?", new String[] { JID });
	}

	void removeRosterItemDialog(final String JID, final String userName) {
		new CustomDialog.Builder(this)
				.setTitle(R.string.deleteRosterItem_title)
				.setMessage(
						getString(R.string.deleteRosterItem_text, userName, JID))
				.setPositiveButton(android.R.string.yes,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								mXxService.removeRosterItem(JID);
							}
						})
				.setNegativeButton(android.R.string.no,
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {

							}
						}).create().show();
	}

	private void editTextDialog(int titleId, CharSequence message, String text,
			final EditOk ok) {
		LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
		View layout = inflater.inflate(R.layout.edittext_dialog, null);

		TextView messageView = (TextView) layout.findViewById(R.id.text);
		messageView.setText(message);
		final EditText input = (EditText) layout.findViewById(R.id.editText);
		input.setTransformationMethod(android.text.method.SingleLineTransformationMethod
				.getInstance());
		input.setText(text);
		new CustomDialog.Builder(this)
				.setTitle(titleId)
				.setView(layout)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								String newName = input.getText().toString();
								if (newName.length() != 0)
									ok.ok(newName);
							}
						})
				.setNegativeButton(android.R.string.cancel,
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {

							}
						}).create().show();
	}

	void renameRosterItemDialog(final String JID, final String userName) {
		editTextDialog(R.string.RenameEntry_title,
				getString(R.string.RenameEntry_summ, userName, JID), userName,
				new EditOk() {
					public void ok(String result) {
						if (mXxService != null)
							mXxService.renameRosterItem(JID, result);
					}
				});
	}

	void renameRosterGroupDialog(final String groupName) {
		editTextDialog(R.string.RenameGroup_title,
				getString(R.string.RenameGroup_summ, groupName), groupName,
				new EditOk() {
					public void ok(String result) {
						if (mXxService != null)
							mXxService.renameRosterGroup(groupName, result);
					}
				});
	}

	void moveRosterItemToGroupDialog(final String jabberID) {
		LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
		View group = inflater
				.inflate(R.layout.moverosterentrytogroupview, null);
		final GroupNameView gv = (GroupNameView) group
				.findViewById(R.id.moverosterentrytogroupview_gv);
		gv.setGroupList(getRosterGroups());
		new CustomDialog.Builder(this)
				.setTitle(R.string.MoveRosterEntryToGroupDialog_title)
				.setView(group)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								L.d("new group: " + gv.getGroupName());
								if (isConnected())
									mXxService.moveRosterItemToGroup(jabberID,
											gv.getGroupName());
							}
						})
				.setNegativeButton(android.R.string.cancel,
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {

							}
						}).create().show();
	}

	@Override
	public void onNetChange() {
		if (NetUtil.getNetworkState(this) == NetUtil.NETWORN_NONE) {
			T.showShort(this, R.string.net_error_tip);
			mNetErrorView.setVisibility(View.VISIBLE);
		} else {
			mNetErrorView.setVisibility(View.GONE);
		}
	}

	private void setStatusImage(boolean isConnected) {
		if (!isConnected) {
			mTitleStatusView.setVisibility(View.GONE);
			return;
		}
		String statusMode = PreferenceUtils.getPrefString(this,
				PreferenceConstants.STATUS_MODE, PreferenceConstants.AVAILABLE);
		int statusId = mStatusMap.get(statusMode);
		if (statusId == -1) {
			mTitleStatusView.setVisibility(View.GONE);
		} else {
			mTitleStatusView.setVisibility(View.VISIBLE);
			mTitleStatusView.setImageResource(statusId);
		}
	}

	@Override
	public void connectionStatusChanged(int connectedState, String reason) {
		switch (connectedState) {
		case XXService.CONNECTED:
			mTitleNameView.setText(XMPPHelper.splitJidAndServer(PreferenceUtils
					.getPrefString(MainActivity.this,
							PreferenceConstants.ACCOUNT, "")));
			mTitleProgressBar.setVisibility(View.GONE);
			// mTitleStatusView.setVisibility(View.GONE);
			setStatusImage(true);
			break;
		case XXService.CONNECTING:
			mTitleNameView.setText(R.string.login_prompt_msg);
			mTitleProgressBar.setVisibility(View.VISIBLE);
			mTitleStatusView.setVisibility(View.GONE);
			break;
		case XXService.DISCONNECTED:
			mTitleNameView.setText(R.string.login_prompt_no);
			mTitleProgressBar.setVisibility(View.GONE);
			mTitleStatusView.setVisibility(View.GONE);
			T.showLong(this, reason);
			break;

		default:
			break;
		}
	}

	@Override
	public XXService getService() {
		return mXxService;
	}

	@Override
	public MainActivity getMainActivity() {
		return this;
	}

	public void updateRoster() {
		mRosterAdapter.requery();
	}

	private class RosterObserver extends ContentObserver {
		public RosterObserver() {
			super(mainHandler);
		}

		public void onChange(boolean selfChange) {
			L.d(MainActivity.class, "RosterObserver.onChange: " + selfChange);
			if (mRosterAdapter != null)
				mainHandler.postDelayed(new Runnable() {
					public void run() {
						updateRoster();
					}
				}, 100);
		}
	}

	private class GetDataTask extends AsyncTask<Void, Void, String[]> {
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
		}

		@Override
		protected String[] doInBackground(Void... params) {
			// Simulates a background job.
			if (!isConnected()) {// Reconnection
				String usr = PreferenceUtils.getPrefString(MainActivity.this,
						PreferenceConstants.ACCOUNT, "");
				String password = PreferenceUtils.getPrefString(
						MainActivity.this, PreferenceConstants.PASSWORD, "");
				mXxService.Login(usr, password);
			}
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
			}
			return null;
		}

		@Override
		protected void onPostExecute(String[] result) {
			// Do some stuff here
			// Call onRefreshComplete when the list has been refreshed.
			mRosterAdapter.requery();
			mPullRefreshScrollView.onRefreshComplete();

			T.showShort(MainActivity.this, "flush success!");
			super.onPostExecute(result);
		}
	}

}
