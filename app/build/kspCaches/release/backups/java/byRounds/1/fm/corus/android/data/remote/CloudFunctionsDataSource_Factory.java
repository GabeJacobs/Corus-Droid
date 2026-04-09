package fm.corus.android.data.remote;

import com.google.firebase.functions.FirebaseFunctions;
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
public final class CloudFunctionsDataSource_Factory implements Factory<CloudFunctionsDataSource> {
  private final Provider<FirebaseFunctions> functionsProvider;

  public CloudFunctionsDataSource_Factory(Provider<FirebaseFunctions> functionsProvider) {
    this.functionsProvider = functionsProvider;
  }

  @Override
  public CloudFunctionsDataSource get() {
    return newInstance(functionsProvider.get());
  }

  public static CloudFunctionsDataSource_Factory create(
      Provider<FirebaseFunctions> functionsProvider) {
    return new CloudFunctionsDataSource_Factory(functionsProvider);
  }

  public static CloudFunctionsDataSource newInstance(FirebaseFunctions functions) {
    return new CloudFunctionsDataSource(functions);
  }
}
