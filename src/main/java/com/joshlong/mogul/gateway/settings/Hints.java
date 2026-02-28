package com.joshlong.mogul.gateway.settings;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.util.Set;

@Configuration
@ImportRuntimeHints(SettingsConfiguration.Hints.class)
class SettingsConfiguration {

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
			var mcs = MemberCategory.values();
			for (var cl : Set.of(Setting.class, SettingsPage.class))
				hints.reflection().registerType(cl, mcs);
		}

	}

}
