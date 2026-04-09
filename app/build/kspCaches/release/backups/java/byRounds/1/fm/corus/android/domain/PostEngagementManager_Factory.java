package fm.corus.android.domain;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.repository.PostRepository;
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
public final class PostEngagementManager_Factory implements Factory<PostEngagementManager> {
  private final Provider<PostRepository> postRepositoryProvider;

  public PostEngagementManager_Factory(Provider<PostRepository> postRepositoryProvider) {
    this.postRepositoryProvider = postRepositoryProvider;
  }

  @Override
  public PostEngagementManager get() {
    return newInstance(postRepositoryProvider.get());
  }

  public static PostEngagementManager_Factory create(
      Provider<PostRepository> postRepositoryProvider) {
    return new PostEngagementManager_Factory(postRepositoryProvider);
  }

  public static PostEngagementManager newInstance(PostRepository postRepository) {
    return new PostEngagementManager(postRepository);
  }
}
