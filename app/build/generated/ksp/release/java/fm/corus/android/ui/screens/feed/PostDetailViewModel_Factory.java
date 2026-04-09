package fm.corus.android.ui.screens.feed;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.repository.AuthRepository;
import fm.corus.android.data.repository.PostRepository;
import fm.corus.android.data.repository.UserRepository;
import fm.corus.android.domain.PostEngagementManager;
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
public final class PostDetailViewModel_Factory implements Factory<PostDetailViewModel> {
  private final Provider<PostRepository> postRepositoryProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  private final Provider<UserRepository> userRepositoryProvider;

  private final Provider<PostEngagementManager> engagementManagerProvider;

  public PostDetailViewModel_Factory(Provider<PostRepository> postRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<PostEngagementManager> engagementManagerProvider) {
    this.postRepositoryProvider = postRepositoryProvider;
    this.authRepositoryProvider = authRepositoryProvider;
    this.userRepositoryProvider = userRepositoryProvider;
    this.engagementManagerProvider = engagementManagerProvider;
  }

  @Override
  public PostDetailViewModel get() {
    return newInstance(postRepositoryProvider.get(), authRepositoryProvider.get(), userRepositoryProvider.get(), engagementManagerProvider.get());
  }

  public static PostDetailViewModel_Factory create(Provider<PostRepository> postRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<PostEngagementManager> engagementManagerProvider) {
    return new PostDetailViewModel_Factory(postRepositoryProvider, authRepositoryProvider, userRepositoryProvider, engagementManagerProvider);
  }

  public static PostDetailViewModel newInstance(PostRepository postRepository,
      AuthRepository authRepository, UserRepository userRepository,
      PostEngagementManager engagementManager) {
    return new PostDetailViewModel(postRepository, authRepository, userRepository, engagementManager);
  }
}
