package fm.corus.android.ui.screens.findpeople;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.remote.CloudFunctionsDataSource;
import fm.corus.android.data.repository.AuthRepository;
import fm.corus.android.data.repository.UserRepository;
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
public final class BotListViewModel_Factory implements Factory<BotListViewModel> {
  private final Provider<CloudFunctionsDataSource> cloudFunctionsProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  private final Provider<UserRepository> userRepositoryProvider;

  public BotListViewModel_Factory(Provider<CloudFunctionsDataSource> cloudFunctionsProvider,
      Provider<AuthRepository> authRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider) {
    this.cloudFunctionsProvider = cloudFunctionsProvider;
    this.authRepositoryProvider = authRepositoryProvider;
    this.userRepositoryProvider = userRepositoryProvider;
  }

  @Override
  public BotListViewModel get() {
    return newInstance(cloudFunctionsProvider.get(), authRepositoryProvider.get(), userRepositoryProvider.get());
  }

  public static BotListViewModel_Factory create(
      Provider<CloudFunctionsDataSource> cloudFunctionsProvider,
      Provider<AuthRepository> authRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider) {
    return new BotListViewModel_Factory(cloudFunctionsProvider, authRepositoryProvider, userRepositoryProvider);
  }

  public static BotListViewModel newInstance(CloudFunctionsDataSource cloudFunctions,
      AuthRepository authRepository, UserRepository userRepository) {
    return new BotListViewModel(cloudFunctions, authRepository, userRepository);
  }
}
