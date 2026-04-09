package fm.corus.android.ui.screens.settings;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.local.PreferencesDataStore;
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
public final class AppearanceSettingsViewModel_Factory implements Factory<AppearanceSettingsViewModel> {
  private final Provider<PreferencesDataStore> preferencesDataStoreProvider;

  public AppearanceSettingsViewModel_Factory(
      Provider<PreferencesDataStore> preferencesDataStoreProvider) {
    this.preferencesDataStoreProvider = preferencesDataStoreProvider;
  }

  @Override
  public AppearanceSettingsViewModel get() {
    return newInstance(preferencesDataStoreProvider.get());
  }

  public static AppearanceSettingsViewModel_Factory create(
      Provider<PreferencesDataStore> preferencesDataStoreProvider) {
    return new AppearanceSettingsViewModel_Factory(preferencesDataStoreProvider);
  }

  public static AppearanceSettingsViewModel newInstance(PreferencesDataStore preferencesDataStore) {
    return new AppearanceSettingsViewModel(preferencesDataStore);
  }
}
