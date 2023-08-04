package com.way.util;

import com.way.xx.R;

public enum StatusMode {
	offline(R.string.status_offline, -1), // offline
	dnd(R.string.status_dnd, R.drawable.status_shield), // Do not disturb
	xa(R.string.status_xa, R.drawable.status_invisible), // stealth
	away(R.string.status_away, R.drawable.status_leave), // leave
	available(R.string.status_online, R.drawable.status_online), // online
	chat(R.string.status_chat, R.drawable.status_qme);// Q me

	private final int textId;
	private final int drawableId;

	StatusMode(int textId, int drawableId) {
		this.textId = textId;
		this.drawableId = drawableId;
	}

	public int getTextId() {
		return textId;
	}

	public int getDrawableId() {
		return drawableId;
	}

	public String toString() {
		return name();
	}

	public static StatusMode fromString(String status) {
		return StatusMode.valueOf(status);
	}

}
