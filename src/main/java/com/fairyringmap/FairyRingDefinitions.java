/*
 * Copyright (c) 2026, Joe Krupa
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package com.fairyringmap;

import java.util.List;
import lombok.Getter;

/** Root of {@code /FairyRingMap/FairyRingDefinitions.json}. */
@Getter
public class FairyRingDefinitions
{
	private MapDefinition map;
	private InsetDefinition inset;
	private List<RingDefinition> rings;
}
