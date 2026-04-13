package fm.corus.android.ui.screens.settings;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.domain.MusicServicePreference;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  private final Provider<MusicServicePreference> musicServicePreferenceProvider;

  public SettingsViewModel_Factory(
      Provider<MusicServicePreference> musicServicePreferenceProvider) {
    this.musicServicePreferenceProvider = musicServicePreferenceProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(musicServicePreferenceProvider.get());
  }

  public static SettingsViewModel_Factory create(
      Provider<MusicServicePreference> musicServicePreferenceProvider) {
    return new SettingsViewModel_Factory(musicServicePreferenceProvider);
  }

  public static SettingsViewModel newInstance(MusicServicePreference musicServicePreference) {
    return new SettingsViewModel(musicServicePreference);
  }
}
