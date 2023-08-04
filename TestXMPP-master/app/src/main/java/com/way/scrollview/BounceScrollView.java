package com.way.scrollview;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.TranslateAnimation;
import android.widget.ScrollView;

/**
 * ScrollView bounce effect implementation
 */
public class BounceScrollView extends ScrollView {
	private View inner;// innerView

	private float y;// The y coordinate when I click

	private Rect normal = new Rect();

	private boolean isCount = false;// computer

	public BounceScrollView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	/***
	 *
	 * onFinishInflate
	 */
	@Override
	protected void onFinishInflate() {
		if (getChildCount() > 0) {
			inner = getChildAt(0);
		}
	}

	/***
	 * 监听touch
	 */
	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		if (inner != null) {
			commOnTouchEvent(ev);
		}

		return super.onTouchEvent(ev);
	}

	/***
	 * 触摸事件
	 * 
	 * @param e
	 */
	public void commOnTouchEvent(MotionEvent e) {
		int action = e.getAction();
		switch (action) {
		case MotionEvent.ACTION_DOWN:
			break;
		case MotionEvent.ACTION_UP:
			// 手指松开.
			if (isNeedAnimation()) {
				animation();
				isCount = false;
			}
			break;
		/***
		 * get position
		 */
		case MotionEvent.ACTION_MOVE:
			final float preY = y;// The y coordinate when pressed
			float nowY = e.getY();
			int deltaY = (int) (preY - nowY);
			if (!isCount) {
				deltaY = 0;
			}

			y = nowY;
			// At this point, move the layout
			if (isNeedMove()) {
				// Initialize the header rectangle
				if (normal.isEmpty()) {
					// Save the normal layout location
					normal.set(inner.getLeft(), inner.getTop(),
							inner.getRight(), inner.getBottom());
				}
				inner.layout(inner.getLeft(), inner.getTop() - deltaY / 2,
						inner.getRight(), inner.getBottom() - deltaY / 2);
			}
			isCount = true;
			break;

		default:
			break;
		}
	}

	/***
	 * Retraction animation
	 */
	public void animation() {
		// Turn on the motion animation
		TranslateAnimation ta = new TranslateAnimation(0, 0, inner.getTop(),
				normal.top);
		ta.setDuration(200);
		inner.startAnimation(ta);
		// Set back to the normal layout position
		inner.layout(normal.left, normal.top, normal.right, normal.bottom);
		normal.setEmpty();

	}

	// Whether to enable animation
	public boolean isNeedAnimation() {
		return !normal.isEmpty();
	}

	/***
	 *
	 * 
	 * getHeight()：获取的是屏幕的高度
	 * 
	 * @return
	 */
	public boolean isNeedMove() {
		int offset = inner.getMeasuredHeight() - getHeight();
		int scrollY = getScrollY();
		if (scrollY == 0 || scrollY == offset) {
			return true;
		}
		return false;
	}

}
