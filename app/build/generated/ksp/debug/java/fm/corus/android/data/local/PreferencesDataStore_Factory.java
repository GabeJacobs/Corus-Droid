package fm.corus.android.data.local;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class PreferencesDataStore_Factory implements Factory<PreferencesDataStore> {
  private final Provider<DataStore<Preferences>> dataStoreProvider;

  public PreferencesDataStore_Factory(Provider<DataStore<Preferences>> dataStoreProvider) {
    this.dataStoreProvider = dataStoreProvider;
  }

  @Override
  public PreferencesDataStore get() {
    return newInstance(dataStoreProvider.get());
  }

  public static PreferencesDataStore_Factory create(
      Provider<DataStore<Preferences>> dataStoreProvider) {
    return new PreferencesDataStore_Factory(dataStoreProvider);
  }

  public static PreferencesDataStore newInstance(DataStore<Preferences> dataStore) {
    return new PreferencesDataStore(dataStore);
  }
}
