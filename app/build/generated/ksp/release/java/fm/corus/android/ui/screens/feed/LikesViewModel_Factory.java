package fm.corus.android.ui.screens.feed;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.repository.AuthRepository;
import fm.corus.android.data.repository.PostRepository;
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
public final class LikesViewModel_Factory implements Factory<LikesViewModel> {
  private final Provider<PostRepository> postRepositoryProvider;

  private final Provider<UserRepository> userRepositoryProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  public LikesViewModel_Factory(Provider<PostRepository> postRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider) {
    this.postRepositoryProvider = postRepositoryProvider;
    this.userRepositoryProvider = userRepositoryProvider;
    this.authRepositoryProvider = authRepositoryProvider;
  }

  @Override
  public LikesViewModel get() {
    return newInstance(postRepositoryProvider.get(), userRepositoryProvider.get(), authRepositoryProvider.get());
  }

  public static LikesViewModel_Factory create(Provider<PostRepository> postRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider) {
    return new LikesViewModel_Factory(postRepositoryProvider, userRepositoryProvider, authRepositoryProvider);
  }

  public static LikesViewModel newInstance(PostRepository postRepository,
      UserRepository userRepository, AuthRepository authRepository) {
    return new LikesViewModel(postRepository, userRepository, authRepository);
  }
}
