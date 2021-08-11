//
// Copyright (c) 2010-2021 TIBCO Software Inc. All rights reserved.
//

package com.tibco.ep.community.components.ev3;

import java.net.URL;

import com.streambase.sb.operator.Operator.IconKind;

public class EV3AdapterIcons {

	public static URL getIconResource(IconKind iconType) {
		switch (iconType) {
		case CANVAS_OVERLAY_ICON:
			return EV3AdapterIcons.class.getResource("resources/ev3logo48.jpg");
		case PALETTE_ICON_LARGE:
			return EV3AdapterIcons.class.getResource("resources/ev3logo32.jpg");
		case PALETTE_ICON_SMALL:
			return EV3AdapterIcons.class.getResource("resources/ev3logo16.jpg");
		default:
			return null;
		}
	}

}
