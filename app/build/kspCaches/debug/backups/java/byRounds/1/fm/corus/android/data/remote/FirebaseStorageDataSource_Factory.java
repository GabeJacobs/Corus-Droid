package fm.corus.android.data.remote;

import com.google.firebase.storage.FirebaseStorage;
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
public final class FirebaseStorageDataSource_Factory implements Factory<FirebaseStorageDataSource> {
  private final Provider<FirebaseStorage> storageProvider;

  public FirebaseStorageDataSource_Factory(Provider<FirebaseStorage> storageProvider) {
    this.storageProvider = storageProvider;
  }

  @Override
  public FirebaseStorageDataSource get() {
    return newInstance(storageProvider.get());
  }

  public static FirebaseStorageDataSource_Factory create(
      Provider<FirebaseStorage> storageProvider) {
    return new FirebaseStorageDataSource_Factory(storageProvider);
  }

  public static FirebaseStorageDataSource newInstance(FirebaseStorage storage) {
    return new FirebaseStorageDataSource(storage);
  }
}
