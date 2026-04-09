package fm.corus.android.ui.screens.profile;

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
public final class FollowListViewModel_Factory implements Factory<FollowListViewModel> {
  private final Provider<UserRepository> userRepositoryProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  private final Provider<CloudFunctionsDataSource> cloudFunctionsProvider;

  public FollowListViewModel_Factory(Provider<UserRepository> userRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider,
      Provider<CloudFunctionsDataSource> cloudFunctionsProvider) {
    this.userRepositoryProvider = userRepositoryProvider;
    this.authRepositoryProvider = authRepositoryProvider;
    this.cloudFunctionsProvider = cloudFunctionsProvider;
  }

  @Override
  public FollowListViewModel get() {
    return newInstance(userRepositoryProvider.get(), authRepositoryProvider.get(), cloudFunctionsProvider.get());
  }

  public static FollowListViewModel_Factory create(Provider<UserRepository> userRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider,
      Provider<CloudFunctionsDataSource> cloudFunctionsProvider) {
    return new FollowListViewModel_Factory(userRepositoryProvider, authRepositoryProvider, cloudFunctionsProvider);
  }

  public static FollowListViewModel newInstance(UserRepository userRepository,
      AuthRepository authRepository, CloudFunctionsDataSource cloudFunctions) {
    return new FollowListViewModel(userRepository, authRepository, cloudFunctions);
  }
}
