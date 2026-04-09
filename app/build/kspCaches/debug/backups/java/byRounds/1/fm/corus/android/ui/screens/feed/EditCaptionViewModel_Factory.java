package fm.corus.android.ui.screens.feed;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
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
public final class EditCaptionViewModel_Factory implements Factory<EditCaptionViewModel> {
  private final Provider<PostRepository> postRepositoryProvider;

  private final Provider<UserRepository> userRepositoryProvider;

  public EditCaptionViewModel_Factory(Provider<PostRepository> postRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider) {
    this.postRepositoryProvider = postRepositoryProvider;
    this.userRepositoryProvider = userRepositoryProvider;
  }

  @Override
  public EditCaptionViewModel get() {
    return newInstance(postRepositoryProvider.get(), userRepositoryProvider.get());
  }

  public static EditCaptionViewModel_Factory create(Provider<PostRepository> postRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider) {
    return new EditCaptionViewModel_Factory(postRepositoryProvider, userRepositoryProvider);
  }

  public static EditCaptionViewModel newInstance(PostRepository postRepository,
      UserRepository userRepository) {
    return new EditCaptionViewModel(postRepository, userRepository);
  }
}
