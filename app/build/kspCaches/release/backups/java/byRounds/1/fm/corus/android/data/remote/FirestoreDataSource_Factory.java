package fm.corus.android.data.remote;

import com.google.firebase.firestore.FirebaseFirestore;
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
public final class FirestoreDataSource_Factory implements Factory<FirestoreDataSource> {
  private final Provider<FirebaseFirestore> firestoreProvider;

  public FirestoreDataSource_Factory(Provider<FirebaseFirestore> firestoreProvider) {
    this.firestoreProvider = firestoreProvider;
  }

  @Override
  public FirestoreDataSource get() {
    return newInstance(firestoreProvider.get());
  }

  public static FirestoreDataSource_Factory create(Provider<FirebaseFirestore> firestoreProvider) {
    return new FirestoreDataSource_Factory(firestoreProvider);
  }

  public static FirestoreDataSource newInstance(FirebaseFirestore firestore) {
    return new FirestoreDataSource(firestore);
  }
}
