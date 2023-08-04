package com.way.activity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import android.content.AsyncQueryHandler;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.way.adapter.ChatAdapter;
import com.way.adapter.FaceAdapter;
import com.way.adapter.FacePageAdeapter;
import com.way.app.XXApp;
import com.way.db.ChatProvider;
import com.way.db.ChatProvider.ChatConstants;
import com.way.db.RosterProvider;
import com.way.service.IConnectionStatusCallback;
import com.way.service.XXService;
import com.way.swipeback.SwipeBackActivity;
import com.way.util.L;
import com.way.util.PreferenceConstants;
import com.way.util.PreferenceUtils;
import com.way.util.StatusMode;
import com.way.util.T;
import com.way.util.XMPPHelper;
import com.way.view.CirclePageIndicator;
import com.way.xlistview.MsgListView;
import com.way.xlistview.MsgListView.IXListViewListener;
import com.way.xx.R;

public class ChatActivity extends SwipeBackActivity implements OnTouchListener,
		OnClickListener, IXListViewListener, IConnectionStatusCallback {
	public static final String INTENT_EXTRA_USERNAME = ChatActivity.class
			.getName() + ".username";// key
	private MsgListView mMsgListView;// ListView
	private ViewPager mFaceViewPager;// ViewPager
	private int mCurrentPage = 0;// Current Expression page
	private boolean mIsFaceShow = false;// Whether to display facial expressions
	private Button mSendMsgBtn;// send
	private ImageButton mFaceSwitchBtn;
	private TextView mTitleNameView;// toolbar
	private ImageView mTitleStatusView;
	private EditText mChatEditText;// input
	private LinearLayout mFaceRoot;// The container
	private WindowManager.LayoutParams mWindowNanagerParams;
	private InputMethodManager mInputMethodManager;
	private List<String> mFaceMapKeys;// arrays
	private String mWithJabberID = null;// userId

	private static final String[] PROJECTION_FROM = new String[] {
			ChatProvider.ChatConstants._ID, ChatProvider.ChatConstants.DATE,
			ChatProvider.ChatConstants.DIRECTION,
			ChatProvider.ChatConstants.JID, ChatProvider.ChatConstants.MESSAGE,
			ChatProvider.ChatConstants.DELIVERY_STATUS };// query

	private ContentObserver mContactObserver = new ContactObserver();// listener online
	private XXService mXxService;// main server
	ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mXxService = ((XXService.XXBinder) service).getService();
			mXxService.registerConnectionStatusCallback(ChatActivity.this);
			// connection server
			if (!mXxService.isAuthenticated()) {
				String usr = PreferenceUtils.getPrefString(ChatActivity.this,
						PreferenceConstants.ACCOUNT, "");
				String password = PreferenceUtils.getPrefString(
						ChatActivity.this, PreferenceConstants.PASSWORD, "");
				mXxService.Login(usr, password);
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mXxService.unRegisterConnectionStatusCallback();
			mXxService = null;
		}

	};

	/**
	 * unBing server
	 */
	private void unbindXMPPService() {
		try {
			unbindService(mServiceConnection);
		} catch (IllegalArgumentException e) {
			L.e("Service wasn't bound!");
		}
	}

	/**
	 * Bing server
	 */
	private void bindXMPPService() {
		Intent mServiceIntent = new Intent(this, XXService.class);
		Uri chatURI = Uri.parse(mWithJabberID);
		mServiceIntent.setData(chatURI);
		bindService(mServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.chat);
		initData();// initData
		initView();// initView
		initFacePage();// initFacePage
		setChatWindowAdapter();// setChatWindowAdapter
		getContentResolver().registerContentObserver(
				RosterProvider.CONTENT_URI, true, mContactObserver);// databases listener
	}

	@Override
	protected void onResume() {
		super.onResume();
		updateContactStatus();// update
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	// STATUS_QUERY
	private static final String[] STATUS_QUERY = new String[] {
			RosterProvider.RosterConstants.STATUS_MODE,
			RosterProvider.RosterConstants.STATUS_MESSAGE, };

	private void updateContactStatus() {
		Cursor cursor = getContentResolver().query(RosterProvider.CONTENT_URI,
				STATUS_QUERY, RosterProvider.RosterConstants.JID + " = ?",
				new String[] { mWithJabberID }, null);
		int MODE_IDX = cursor
				.getColumnIndex(RosterProvider.RosterConstants.STATUS_MODE);
		int MSG_IDX = cursor
				.getColumnIndex(RosterProvider.RosterConstants.STATUS_MESSAGE);

		if (cursor.getCount() == 1) {
			cursor.moveToFirst();
			int status_mode = cursor.getInt(MODE_IDX);
			String status_message = cursor.getString(MSG_IDX);
			L.d("contact status changed: " + status_mode + " " + status_message);
			mTitleNameView.setText(XMPPHelper.splitJidAndServer(getIntent()
					.getStringExtra(INTENT_EXTRA_USERNAME)));
			int statusId = StatusMode.values()[status_mode].getDrawableId();
			if (statusId != -1) {
				mTitleStatusView.setImageResource(statusId);
				mTitleStatusView.setVisibility(View.VISIBLE);
			} else {
				mTitleStatusView.setVisibility(View.GONE);
			}
		}
		cursor.close();
	}

	/**
	 * Listen for changes in the contact database
	 * 
	 */
	private class ContactObserver extends ContentObserver {
		public ContactObserver() {
			super(new Handler());
		}

		public void onChange(boolean selfChange) {
			L.d("ContactObserver.onChange: " + selfChange);
			updateContactStatus();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (hasWindowFocus())
			unbindXMPPService();// unBing server
		getContentResolver().unregisterContentObserver(mContactObserver);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus)
			bindXMPPService();
		else
			unbindXMPPService();
	}

	private void initData() {
		mWithJabberID = getIntent().getDataString().toLowerCase();
		Set<String> keySet = XXApp.getInstance().getFaceMap().keySet();
		mFaceMapKeys = new ArrayList<String>();
		mFaceMapKeys.addAll(keySet);
	}

	/**
	 * Set the chat Adapter
	 */
	private void setChatWindowAdapter() {
		String selection = ChatConstants.JID + "='" + mWithJabberID + "'";
		// AsyncQueryHandler
		new AsyncQueryHandler(getContentResolver()) {

			@Override
			protected void onQueryComplete(int token, Object cookie,
					Cursor cursor) {
				ListAdapter adapter = new ChatAdapter(ChatActivity.this,
						cursor, PROJECTION_FROM);
				mMsgListView.setAdapter(adapter);
				mMsgListView.setSelection(adapter.getCount() - 1);
			}

		}.startQuery(0, null, ChatProvider.CONTENT_URI, PROJECTION_FROM,
				selection, null, null);
	}

	private void initView() {
		mInputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
		mWindowNanagerParams = getWindow().getAttributes();

		mMsgListView = (MsgListView) findViewById(R.id.msg_listView);
		mMsgListView.setOnTouchListener(this);
		mMsgListView.setPullLoadEnable(false);
		mMsgListView.setXListViewListener(this);
		mSendMsgBtn = (Button) findViewById(R.id.send);
		mFaceSwitchBtn = (ImageButton) findViewById(R.id.face_switch_btn);
		mChatEditText = (EditText) findViewById(R.id.input);
		mFaceRoot = (LinearLayout) findViewById(R.id.face_ll);
		mFaceViewPager = (ViewPager) findViewById(R.id.face_pager);
		mChatEditText.setOnTouchListener(this);
		mTitleNameView = (TextView) findViewById(R.id.ivTitleName);
		mTitleStatusView = (ImageView) findViewById(R.id.ivTitleStatus);
		mChatEditText.setOnKeyListener(new OnKeyListener() {

			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				// TODO Auto-generated method stub
				if (keyCode == KeyEvent.KEYCODE_BACK) {
					if (mWindowNanagerParams.softInputMode == WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
							|| mIsFaceShow) {
						mFaceRoot.setVisibility(View.GONE);
						mIsFaceShow = false;
						return true;
					}
				}
				return false;
			}
		});
		mChatEditText.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				// TODO Auto-generated method stub
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
				// TODO Auto-generated method stub

			}

			@Override
			public void afterTextChanged(Editable s) {
				// TODO Auto-generated method stub
				if (s.length() > 0) {
					mSendMsgBtn.setEnabled(true);
				} else {
					mSendMsgBtn.setEnabled(false);
				}
			}
		});
		mFaceSwitchBtn.setOnClickListener(this);
		mSendMsgBtn.setOnClickListener(this);
	}

	@Override
	public void onRefresh() {
		mMsgListView.stopRefresh();
	}

	@Override
	public void onLoadMore() {
		// do nothing
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.face_switch_btn:
			if (!mIsFaceShow) {
				mInputMethodManager.hideSoftInputFromWindow(
						mChatEditText.getWindowToken(), 0);
				try {
					Thread.sleep(80);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				mFaceRoot.setVisibility(View.VISIBLE);
				mFaceSwitchBtn.setImageResource(R.drawable.aio_keyboard);
				mIsFaceShow = true;
			} else {
				mFaceRoot.setVisibility(View.GONE);
				mInputMethodManager.showSoftInput(mChatEditText, 0);
				mFaceSwitchBtn
						.setImageResource(R.drawable.qzone_edit_face_drawable);
				mIsFaceShow = false;
			}
			break;
		case R.id.send:// sendMessage
			sendMessageIfNotNull();
			break;
		default:
			break;
		}
	}

	private void sendMessageIfNotNull() {
		if (mChatEditText.getText().length() >= 1) {
			if (mXxService != null) {
				mXxService.sendMessage(mWithJabberID, mChatEditText.getText()
						.toString());
				if (!mXxService.isAuthenticated())
					T.showShort(this, "The message has been saved");
			}
			mChatEditText.setText(null);
			mSendMsgBtn.setEnabled(false);
		}
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		switch (v.getId()) {
		case R.id.msg_listView:
			mInputMethodManager.hideSoftInputFromWindow(
					mChatEditText.getWindowToken(), 0);
			mFaceSwitchBtn
					.setImageResource(R.drawable.qzone_edit_face_drawable);
			mFaceRoot.setVisibility(View.GONE);
			mIsFaceShow = false;
			break;
		case R.id.input:
			mInputMethodManager.showSoftInput(mChatEditText, 0);
			mFaceSwitchBtn
					.setImageResource(R.drawable.qzone_edit_face_drawable);
			mFaceRoot.setVisibility(View.GONE);
			mIsFaceShow = false;
			break;

		default:
			break;
		}
		return false;
	}

	private void initFacePage() {
		// TODO Auto-generated method stub
		List<View> lv = new ArrayList<View>();
		for (int i = 0; i < XXApp.NUM_PAGE; ++i)
			lv.add(getGridView(i));
		FacePageAdeapter adapter = new FacePageAdeapter(lv);
		mFaceViewPager.setAdapter(adapter);
		mFaceViewPager.setCurrentItem(mCurrentPage);
		CirclePageIndicator indicator = (CirclePageIndicator) findViewById(R.id.indicator);
		indicator.setViewPager(mFaceViewPager);
		adapter.notifyDataSetChanged();
		mFaceRoot.setVisibility(View.GONE);
		indicator.setOnPageChangeListener(new OnPageChangeListener() {

			@Override
			public void onPageSelected(int arg0) {
				mCurrentPage = arg0;
			}

			@Override
			public void onPageScrolled(int arg0, float arg1, int arg2) {
				// do nothing
			}

			@Override
			public void onPageScrollStateChanged(int arg0) {
				// do nothing
			}
		});

	}

	private GridView getGridView(int i) {
		// TODO Auto-generated method stub
		GridView gv = new GridView(this);
		gv.setNumColumns(7);
		gv.setSelector(new ColorDrawable(Color.TRANSPARENT));
		gv.setBackgroundColor(Color.TRANSPARENT);
		gv.setCacheColorHint(Color.TRANSPARENT);
		gv.setHorizontalSpacing(1);
		gv.setVerticalSpacing(1);
		gv.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
				LayoutParams.MATCH_PARENT));
		gv.setGravity(Gravity.CENTER);
		gv.setAdapter(new FaceAdapter(this, i));
		gv.setOnTouchListener(forbidenScroll());
		gv.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
					long arg3) {
				// TODO Auto-generated method stub
				if (arg2 == XXApp.NUM) {// 删除键的位置
					int selection = mChatEditText.getSelectionStart();
					String text = mChatEditText.getText().toString();
					if (selection > 0) {
						String text2 = text.substring(selection - 1);
						if ("]".equals(text2)) {
							int start = text.lastIndexOf("[");
							int end = selection;
							mChatEditText.getText().delete(start, end);
							return;
						}
						mChatEditText.getText()
								.delete(selection - 1, selection);
					}
				} else {
					int count = mCurrentPage * XXApp.NUM + arg2;
					Bitmap bitmap = BitmapFactory.decodeResource(
							getResources(), (Integer) XXApp.getInstance()
									.getFaceMap().values().toArray()[count]);
					if (bitmap != null) {
						int rawHeigh = bitmap.getHeight();
						int rawWidth = bitmap.getHeight();
						int newHeight = 40;
						int newWidth = 40;
						float heightScale = ((float) newHeight) / rawHeigh;
						float widthScale = ((float) newWidth) / rawWidth;
						Matrix matrix = new Matrix();
						matrix.postScale(heightScale, widthScale);
						Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0,
								rawWidth, rawHeigh, matrix, true);
						ImageSpan imageSpan = new ImageSpan(ChatActivity.this,
								newBitmap);
						String emojiStr = mFaceMapKeys.get(count);
						SpannableString spannableString = new SpannableString(
								emojiStr);
						spannableString.setSpan(imageSpan,
								emojiStr.indexOf('['),
								emojiStr.indexOf(']') + 1,
								Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
						mChatEditText.append(spannableString);
					} else {
						String ori = mChatEditText.getText().toString();
						int index = mChatEditText.getSelectionStart();
						StringBuilder stringBuilder = new StringBuilder(ori);
						stringBuilder.insert(index, mFaceMapKeys.get(count));
						mChatEditText.setText(stringBuilder.toString());
						mChatEditText.setSelection(index
								+ mFaceMapKeys.get(count).length());
					}
				}
			}
		});
		return gv;
	}

	// forbidenScroll
	private OnTouchListener forbidenScroll() {
		return new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_MOVE) {
					return true;
				}
				return false;
			}
		};
	}

	@Override
	public void connectionStatusChanged(int connectedState, String reason) {
		// TODO Auto-generated method stub
	}
}
