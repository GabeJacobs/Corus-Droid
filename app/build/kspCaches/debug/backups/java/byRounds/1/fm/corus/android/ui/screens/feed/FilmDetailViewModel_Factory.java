package fm.corus.android.ui.screens.feed;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
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
public final class FilmDetailViewModel_Factory implements Factory<FilmDetailViewModel> {
  private final Provider<PostRepository> postRepositoryProvider;

  public FilmDetailViewModel_Factory(Provider<PostRepository> postRepositoryProvider) {
    this.postRepositoryProvider = postRepositoryProvider;
  }

  @Override
  public FilmDetailViewModel get() {
    return newInstance(postRepositoryProvider.get());
  }

  public static FilmDetailViewModel_Factory create(
      Provider<PostRepository> postRepositoryProvider) {
    return new FilmDetailViewModel_Factory(postRepositoryProvider);
  }

  public static FilmDetailViewModel newInstance(PostRepository postRepository) {
    return new FilmDetailViewModel(postRepository);
  }
}
