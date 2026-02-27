package com.joshlong.mogul.gateway.settings;

import java.util.List;

public record SettingsPage(Boolean valid, String category, List<Setting> settings) {
}
