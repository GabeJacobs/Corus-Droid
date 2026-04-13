package fm.corus.android.ui.screens.feed;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.repository.AuthRepository;
import fm.corus.android.data.repository.MessageRepository;
import fm.corus.android.data.repository.PostRepository;
import fm.corus.android.data.repository.UserRepository;
import fm.corus.android.domain.NowPlayingManager;
import fm.corus.android.domain.PostEngagementManager;
import fm.corus.android.service.AnalyticsService;
import fm.corus.android.service.RemoteConfigService;
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
public final class FeedViewModel_Factory implements Factory<FeedViewModel> {
  private final Provider<PostRepository> postRepositoryProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  private final Provider<PostEngagementManager> engagementManagerProvider;

  private final Provider<UserRepository> userRepositoryProvider;

  private final Provider<MessageRepository> messageRepositoryProvider;

  private final Provider<NowPlayingManager> nowPlayingManagerProvider;

  private final Provider<RemoteConfigService> remoteConfigProvider;

  private final Provider<AnalyticsService> analyticsServiceProvider;

  public FeedViewModel_Factory(Provider<PostRepository> postRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider,
      Provider<PostEngagementManager> engagementManagerProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<MessageRepository> messageRepositoryProvider,
      Provider<NowPlayingManager> nowPlayingManagerProvider,
      Provider<RemoteConfigService> remoteConfigProvider,
      Provider<AnalyticsService> analyticsServiceProvider) {
    this.postRepositoryProvider = postRepositoryProvider;
    this.authRepositoryProvider = authRepositoryProvider;
    this.engagementManagerProvider = engagementManagerProvider;
    this.userRepositoryProvider = userRepositoryProvider;
    this.messageRepositoryProvider = messageRepositoryProvider;
    this.nowPlayingManagerProvider = nowPlayingManagerProvider;
    this.remoteConfigProvider = remoteConfigProvider;
    this.analyticsServiceProvider = analyticsServiceProvider;
  }

  @Override
  public FeedViewModel get() {
    return newInstance(postRepositoryProvider.get(), authRepositoryProvider.get(), engagementManagerProvider.get(), userRepositoryProvider.get(), messageRepositoryProvider.get(), nowPlayingManagerProvider.get(), remoteConfigProvider.get(), analyticsServiceProvider.get());
  }

  public static FeedViewModel_Factory create(Provider<PostRepository> postRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider,
      Provider<PostEngagementManager> engagementManagerProvider,
      Provider<UserRepository> userRepositoryProvider,
      Provider<MessageRepository> messageRepositoryProvider,
      Provider<NowPlayingManager> nowPlayingManagerProvider,
      Provider<RemoteConfigService> remoteConfigProvider,
      Provider<AnalyticsService> analyticsServiceProvider) {
    return new FeedViewModel_Factory(postRepositoryProvider, authRepositoryProvider, engagementManagerProvider, userRepositoryProvider, messageRepositoryProvider, nowPlayingManagerProvider, remoteConfigProvider, analyticsServiceProvider);
  }

  public static FeedViewModel newInstance(PostRepository postRepository,
      AuthRepository authRepository, PostEngagementManager engagementManager,
      UserRepository userRepository, MessageRepository messageRepository,
      NowPlayingManager nowPlayingManager, RemoteConfigService remoteConfig,
      AnalyticsService analyticsService) {
    return new FeedViewModel(postRepository, authRepository, engagementManager, userRepository, messageRepository, nowPlayingManager, remoteConfig, analyticsService);
  }
}
