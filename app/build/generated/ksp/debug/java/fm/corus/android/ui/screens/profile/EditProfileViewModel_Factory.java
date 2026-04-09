package fm.corus.android.ui.screens.profile;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.repository.AuthRepository;
import fm.corus.android.data.repository.PostRepository;
import fm.corus.android.data.repository.SubscriptionRepository;
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
public final class EditProfileViewModel_Factory implements Factory<EditProfileViewModel> {
  private final Provider<UserRepository> userRepositoryProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  private final Provider<PostRepository> postRepositoryProvider;

  private final Provider<SubscriptionRepository> subscriptionRepositoryProvider;

  public EditProfileViewModel_Factory(Provider<UserRepository> userRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider,
      Provider<PostRepository> postRepositoryProvider,
      Provider<SubscriptionRepository> subscriptionRepositoryProvider) {
    this.userRepositoryProvider = userRepositoryProvider;
    this.authRepositoryProvider = authRepositoryProvider;
    this.postRepositoryProvider = postRepositoryProvider;
    this.subscriptionRepositoryProvider = subscriptionRepositoryProvider;
  }

  @Override
  public EditProfileViewModel get() {
    return newInstance(userRepositoryProvider.get(), authRepositoryProvider.get(), postRepositoryProvider.get(), subscriptionRepositoryProvider.get());
  }

  public static EditProfileViewModel_Factory create(Provider<UserRepository> userRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider,
      Provider<PostRepository> postRepositoryProvider,
      Provider<SubscriptionRepository> subscriptionRepositoryProvider) {
    return new EditProfileViewModel_Factory(userRepositoryProvider, authRepositoryProvider, postRepositoryProvider, subscriptionRepositoryProvider);
  }

  public static EditProfileViewModel newInstance(UserRepository userRepository,
      AuthRepository authRepository, PostRepository postRepository,
      SubscriptionRepository subscriptionRepository) {
    return new EditProfileViewModel(userRepository, authRepository, postRepository, subscriptionRepository);
  }
}
