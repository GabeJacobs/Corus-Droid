package fm.corus.android.ui.screens.feed;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.repository.PostRepository;
import fm.corus.android.domain.NowPlayingManager;
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
public final class SongDetailViewModel_Factory implements Factory<SongDetailViewModel> {
  private final Provider<PostRepository> postRepositoryProvider;

  private final Provider<NowPlayingManager> nowPlayingManagerProvider;

  public SongDetailViewModel_Factory(Provider<PostRepository> postRepositoryProvider,
      Provider<NowPlayingManager> nowPlayingManagerProvider) {
    this.postRepositoryProvider = postRepositoryProvider;
    this.nowPlayingManagerProvider = nowPlayingManagerProvider;
  }

  @Override
  public SongDetailViewModel get() {
    return newInstance(postRepositoryProvider.get(), nowPlayingManagerProvider.get());
  }

  public static SongDetailViewModel_Factory create(Provider<PostRepository> postRepositoryProvider,
      Provider<NowPlayingManager> nowPlayingManagerProvider) {
    return new SongDetailViewModel_Factory(postRepositoryProvider, nowPlayingManagerProvider);
  }

  public static SongDetailViewModel newInstance(PostRepository postRepository,
      NowPlayingManager nowPlayingManager) {
    return new SongDetailViewModel(postRepository, nowPlayingManager);
  }
}
