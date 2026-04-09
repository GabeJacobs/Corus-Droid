package fm.corus.android.ui.screens.explore;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.repository.AuthRepository;
import fm.corus.android.data.repository.PostRepository;
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
public final class HashtagFeedViewModel_Factory implements Factory<HashtagFeedViewModel> {
  private final Provider<PostRepository> postRepositoryProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  public HashtagFeedViewModel_Factory(Provider<PostRepository> postRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider) {
    this.postRepositoryProvider = postRepositoryProvider;
    this.authRepositoryProvider = authRepositoryProvider;
  }

  @Override
  public HashtagFeedViewModel get() {
    return newInstance(postRepositoryProvider.get(), authRepositoryProvider.get());
  }

  public static HashtagFeedViewModel_Factory create(Provider<PostRepository> postRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider) {
    return new HashtagFeedViewModel_Factory(postRepositoryProvider, authRepositoryProvider);
  }

  public static HashtagFeedViewModel newInstance(PostRepository postRepository,
      AuthRepository authRepository) {
    return new HashtagFeedViewModel(postRepository, authRepository);
  }
}
