package fm.corus.android;

import android.app.Activity;
import android.app.Service;
import android.view.View;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.storage.FirebaseStorage;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.ViewModelLifecycle;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.android.internal.builders.ServiceComponentBuilder;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories_InternalFactoryFactory_Factory;
import dagger.hilt.android.internal.managers.ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory;
import dagger.hilt.android.internal.managers.SavedStateHandleHolder;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideContextFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.LazyClassKeyMap;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import fm.corus.android.data.local.PreferencesDataStore;
import fm.corus.android.data.remote.CloudFunctionsDataSource;
import fm.corus.android.data.remote.FirebaseStorageDataSource;
import fm.corus.android.data.remote.FirestoreDataSource;
import fm.corus.android.data.remote.TMDBApiService;
import fm.corus.android.data.repository.AuthRepository;
import fm.corus.android.data.repository.ExploreRepository;
import fm.corus.android.data.repository.MessageRepository;
import fm.corus.android.data.repository.NotificationRepository;
import fm.corus.android.data.repository.PostRepository;
import fm.corus.android.data.repository.SpotifyRepository;
import fm.corus.android.data.repository.SubscriptionRepository;
import fm.corus.android.data.repository.TMDBRepository;
import fm.corus.android.data.repository.UserRepository;
import fm.corus.android.di.AppModule_ProvideDataStoreFactory;
import fm.corus.android.di.AppModule_ProvideFirebaseAuthFactory;
import fm.corus.android.di.AppModule_ProvideFirebaseFunctionsFactory;
import fm.corus.android.di.AppModule_ProvideFirebaseMessagingFactory;
import fm.corus.android.di.AppModule_ProvideFirebaseStorageFactory;
import fm.corus.android.di.AppModule_ProvideFirestoreFactory;
import fm.corus.android.di.AppModule_ProvideRemoteConfigFactory;
import fm.corus.android.di.NetworkModule_ProvideHttpClientFactory;
import fm.corus.android.di.NetworkModule_ProvideJsonFactory;
import fm.corus.android.di.RepositoryModule_ProvideFirebaseAnalyticsFactory;
import fm.corus.android.domain.NowPlayingManager;
import fm.corus.android.domain.PostEngagementManager;
import fm.corus.android.service.AnalyticsService;
import fm.corus.android.service.RemoteConfigService;
import fm.corus.android.ui.navigation.MainTabViewModel;
import fm.corus.android.ui.navigation.MainTabViewModel_HiltModules;
import fm.corus.android.ui.navigation.MainTabViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.navigation.MainTabViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.auth.AuthViewModel;
import fm.corus.android.ui.screens.auth.AuthViewModel_HiltModules;
import fm.corus.android.ui.screens.auth.AuthViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.auth.AuthViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.auth.SocialSetupViewModel;
import fm.corus.android.ui.screens.auth.SocialSetupViewModel_HiltModules;
import fm.corus.android.ui.screens.auth.SocialSetupViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.auth.SocialSetupViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.compose.ComposeViewModel;
import fm.corus.android.ui.screens.compose.ComposeViewModel_HiltModules;
import fm.corus.android.ui.screens.compose.ComposeViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.compose.ComposeViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.explore.ExploreViewModel;
import fm.corus.android.ui.screens.explore.ExploreViewModel_HiltModules;
import fm.corus.android.ui.screens.explore.ExploreViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.explore.ExploreViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.explore.HashtagFeedViewModel;
import fm.corus.android.ui.screens.explore.HashtagFeedViewModel_HiltModules;
import fm.corus.android.ui.screens.explore.HashtagFeedViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.explore.HashtagFeedViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.feed.CommentsViewModel;
import fm.corus.android.ui.screens.feed.CommentsViewModel_HiltModules;
import fm.corus.android.ui.screens.feed.CommentsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.feed.CommentsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.feed.EditCaptionViewModel;
import fm.corus.android.ui.screens.feed.EditCaptionViewModel_HiltModules;
import fm.corus.android.ui.screens.feed.EditCaptionViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.feed.EditCaptionViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.feed.FeedViewModel;
import fm.corus.android.ui.screens.feed.FeedViewModel_HiltModules;
import fm.corus.android.ui.screens.feed.FeedViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.feed.FeedViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.feed.FilmDetailViewModel;
import fm.corus.android.ui.screens.feed.FilmDetailViewModel_HiltModules;
import fm.corus.android.ui.screens.feed.FilmDetailViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.feed.FilmDetailViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.feed.LikesViewModel;
import fm.corus.android.ui.screens.feed.LikesViewModel_HiltModules;
import fm.corus.android.ui.screens.feed.LikesViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.feed.LikesViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.feed.PostDetailViewModel;
import fm.corus.android.ui.screens.feed.PostDetailViewModel_HiltModules;
import fm.corus.android.ui.screens.feed.PostDetailViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.feed.PostDetailViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.feed.SongDetailViewModel;
import fm.corus.android.ui.screens.feed.SongDetailViewModel_HiltModules;
import fm.corus.android.ui.screens.feed.SongDetailViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.feed.SongDetailViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.findpeople.BotListViewModel;
import fm.corus.android.ui.screens.findpeople.BotListViewModel_HiltModules;
import fm.corus.android.ui.screens.findpeople.BotListViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.findpeople.BotListViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.findpeople.ContactFriendsListViewModel;
import fm.corus.android.ui.screens.findpeople.ContactFriendsListViewModel_HiltModules;
import fm.corus.android.ui.screens.findpeople.ContactFriendsListViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.findpeople.ContactFriendsListViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.findpeople.FindPeopleViewModel;
import fm.corus.android.ui.screens.findpeople.FindPeopleViewModel_HiltModules;
import fm.corus.android.ui.screens.findpeople.FindPeopleViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.findpeople.FindPeopleViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.findpeople.SuggestedUsersListViewModel;
import fm.corus.android.ui.screens.findpeople.SuggestedUsersListViewModel_HiltModules;
import fm.corus.android.ui.screens.findpeople.SuggestedUsersListViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.findpeople.SuggestedUsersListViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.messaging.MessageThreadViewModel;
import fm.corus.android.ui.screens.messaging.MessageThreadViewModel_HiltModules;
import fm.corus.android.ui.screens.messaging.MessageThreadViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.messaging.MessageThreadViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.messaging.ThreadListViewModel;
import fm.corus.android.ui.screens.messaging.ThreadListViewModel_HiltModules;
import fm.corus.android.ui.screens.messaging.ThreadListViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.messaging.ThreadListViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.notifications.NotificationsViewModel;
import fm.corus.android.ui.screens.notifications.NotificationsViewModel_HiltModules;
import fm.corus.android.ui.screens.notifications.NotificationsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.notifications.NotificationsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.profile.EditProfileViewModel;
import fm.corus.android.ui.screens.profile.EditProfileViewModel_HiltModules;
import fm.corus.android.ui.screens.profile.EditProfileViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.profile.EditProfileViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.profile.FollowListViewModel;
import fm.corus.android.ui.screens.profile.FollowListViewModel_HiltModules;
import fm.corus.android.ui.screens.profile.FollowListViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.profile.FollowListViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.profile.OtherProfileViewModel;
import fm.corus.android.ui.screens.profile.OtherProfileViewModel_HiltModules;
import fm.corus.android.ui.screens.profile.OtherProfileViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.profile.OtherProfileViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.profile.ProfileViewModel;
import fm.corus.android.ui.screens.profile.ProfileViewModel_HiltModules;
import fm.corus.android.ui.screens.profile.ProfileViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.profile.ProfileViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.settings.AppearanceSettingsViewModel;
import fm.corus.android.ui.screens.settings.AppearanceSettingsViewModel_HiltModules;
import fm.corus.android.ui.screens.settings.AppearanceSettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.settings.AppearanceSettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.settings.BlockedUsersViewModel;
import fm.corus.android.ui.screens.settings.BlockedUsersViewModel_HiltModules;
import fm.corus.android.ui.screens.settings.BlockedUsersViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.settings.BlockedUsersViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.settings.ChangePhoneNumberViewModel;
import fm.corus.android.ui.screens.settings.ChangePhoneNumberViewModel_HiltModules;
import fm.corus.android.ui.screens.settings.ChangePhoneNumberViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.settings.ChangePhoneNumberViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.settings.ChangeUsernameViewModel;
import fm.corus.android.ui.screens.settings.ChangeUsernameViewModel_HiltModules;
import fm.corus.android.ui.screens.settings.ChangeUsernameViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.settings.ChangeUsernameViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.settings.FeedbackFormViewModel;
import fm.corus.android.ui.screens.settings.FeedbackFormViewModel_HiltModules;
import fm.corus.android.ui.screens.settings.FeedbackFormViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.settings.FeedbackFormViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.settings.MutedUsersViewModel;
import fm.corus.android.ui.screens.settings.MutedUsersViewModel_HiltModules;
import fm.corus.android.ui.screens.settings.MutedUsersViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.settings.MutedUsersViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import fm.corus.android.ui.screens.subscription.CymbalClubViewModel;
import fm.corus.android.ui.screens.subscription.CymbalClubViewModel_HiltModules;
import fm.corus.android.ui.screens.subscription.CymbalClubViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import fm.corus.android.ui.screens.subscription.CymbalClubViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import io.ktor.client.HttpClient;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;
import kotlinx.serialization.json.Json;

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
public final class DaggerCorusApplication_HiltComponents_SingletonC {
  private DaggerCorusApplication_HiltComponents_SingletonC() {
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ApplicationContextModule applicationContextModule;

    private Builder() {
    }

    public Builder applicationContextModule(ApplicationContextModule applicationContextModule) {
      this.applicationContextModule = Preconditions.checkNotNull(applicationContextModule);
      return this;
    }

    public CorusApplication_HiltComponents.SingletonC build() {
      Preconditions.checkBuilderRequirement(applicationContextModule, ApplicationContextModule.class);
      return new SingletonCImpl(applicationContextModule);
    }
  }

  private static final class ActivityRetainedCBuilder implements CorusApplication_HiltComponents.ActivityRetainedC.Builder {
    private final SingletonCImpl singletonCImpl;

    private SavedStateHandleHolder savedStateHandleHolder;

    private ActivityRetainedCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ActivityRetainedCBuilder savedStateHandleHolder(
        SavedStateHandleHolder savedStateHandleHolder) {
      this.savedStateHandleHolder = Preconditions.checkNotNull(savedStateHandleHolder);
      return this;
    }

    @Override
    public CorusApplication_HiltComponents.ActivityRetainedC build() {
      Preconditions.checkBuilderRequirement(savedStateHandleHolder, SavedStateHandleHolder.class);
      return new ActivityRetainedCImpl(singletonCImpl, savedStateHandleHolder);
    }
  }

  private static final class ActivityCBuilder implements CorusApplication_HiltComponents.ActivityC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private Activity activity;

    private ActivityCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ActivityCBuilder activity(Activity activity) {
      this.activity = Preconditions.checkNotNull(activity);
      return this;
    }

    @Override
    public CorusApplication_HiltComponents.ActivityC build() {
      Preconditions.checkBuilderRequirement(activity, Activity.class);
      return new ActivityCImpl(singletonCImpl, activityRetainedCImpl, activity);
    }
  }

  private static final class FragmentCBuilder implements CorusApplication_HiltComponents.FragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private Fragment fragment;

    private FragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public FragmentCBuilder fragment(Fragment fragment) {
      this.fragment = Preconditions.checkNotNull(fragment);
      return this;
    }

    @Override
    public CorusApplication_HiltComponents.FragmentC build() {
      Preconditions.checkBuilderRequirement(fragment, Fragment.class);
      return new FragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragment);
    }
  }

  private static final class ViewWithFragmentCBuilder implements CorusApplication_HiltComponents.ViewWithFragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private View view;

    private ViewWithFragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;
    }

    @Override
    public ViewWithFragmentCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public CorusApplication_HiltComponents.ViewWithFragmentC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewWithFragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl, view);
    }
  }

  private static final class ViewCBuilder implements CorusApplication_HiltComponents.ViewC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private View view;

    private ViewCBuilder(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public ViewCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public CorusApplication_HiltComponents.ViewC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, view);
    }
  }

  private static final class ViewModelCBuilder implements CorusApplication_HiltComponents.ViewModelC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private SavedStateHandle savedStateHandle;

    private ViewModelLifecycle viewModelLifecycle;

    private ViewModelCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ViewModelCBuilder savedStateHandle(SavedStateHandle handle) {
      this.savedStateHandle = Preconditions.checkNotNull(handle);
      return this;
    }

    @Override
    public ViewModelCBuilder viewModelLifecycle(ViewModelLifecycle viewModelLifecycle) {
      this.viewModelLifecycle = Preconditions.checkNotNull(viewModelLifecycle);
      return this;
    }

    @Override
    public CorusApplication_HiltComponents.ViewModelC build() {
      Preconditions.checkBuilderRequirement(savedStateHandle, SavedStateHandle.class);
      Preconditions.checkBuilderRequirement(viewModelLifecycle, ViewModelLifecycle.class);
      return new ViewModelCImpl(singletonCImpl, activityRetainedCImpl, savedStateHandle, viewModelLifecycle);
    }
  }

  private static final class ServiceCBuilder implements CorusApplication_HiltComponents.ServiceC.Builder {
    private final SingletonCImpl singletonCImpl;

    private Service service;

    private ServiceCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ServiceCBuilder service(Service service) {
      this.service = Preconditions.checkNotNull(service);
      return this;
    }

    @Override
    public CorusApplication_HiltComponents.ServiceC build() {
      Preconditions.checkBuilderRequirement(service, Service.class);
      return new ServiceCImpl(singletonCImpl, service);
    }
  }

  private static final class ViewWithFragmentCImpl extends CorusApplication_HiltComponents.ViewWithFragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private final ViewWithFragmentCImpl viewWithFragmentCImpl = this;

    private ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;


    }
  }

  private static final class FragmentCImpl extends CorusApplication_HiltComponents.FragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl = this;

    private FragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        Fragment fragmentParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return activityCImpl.getHiltInternalFactoryFactory();
    }

    @Override
    public ViewWithFragmentComponentBuilder viewWithFragmentComponentBuilder() {
      return new ViewWithFragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl);
    }
  }

  private static final class ViewCImpl extends CorusApplication_HiltComponents.ViewC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final ViewCImpl viewCImpl = this;

    private ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }
  }

  private static final class ActivityCImpl extends CorusApplication_HiltComponents.ActivityC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl = this;

    private ActivityCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Map<Class<?>, Boolean> getViewModelKeys() {
      return LazyClassKeyMap.<Boolean>of(ImmutableMap.<String, Boolean>builderWithExpectedSize(31).put(AppearanceSettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, AppearanceSettingsViewModel_HiltModules.KeyModule.provide()).put(AuthViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, AuthViewModel_HiltModules.KeyModule.provide()).put(BlockedUsersViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, BlockedUsersViewModel_HiltModules.KeyModule.provide()).put(BotListViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, BotListViewModel_HiltModules.KeyModule.provide()).put(ChangePhoneNumberViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, ChangePhoneNumberViewModel_HiltModules.KeyModule.provide()).put(ChangeUsernameViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, ChangeUsernameViewModel_HiltModules.KeyModule.provide()).put(CommentsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, CommentsViewModel_HiltModules.KeyModule.provide()).put(ComposeViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, ComposeViewModel_HiltModules.KeyModule.provide()).put(ContactFriendsListViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, ContactFriendsListViewModel_HiltModules.KeyModule.provide()).put(CymbalClubViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, CymbalClubViewModel_HiltModules.KeyModule.provide()).put(EditCaptionViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, EditCaptionViewModel_HiltModules.KeyModule.provide()).put(EditProfileViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, EditProfileViewModel_HiltModules.KeyModule.provide()).put(ExploreViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, ExploreViewModel_HiltModules.KeyModule.provide()).put(FeedViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, FeedViewModel_HiltModules.KeyModule.provide()).put(FeedbackFormViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, FeedbackFormViewModel_HiltModules.KeyModule.provide()).put(FilmDetailViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, FilmDetailViewModel_HiltModules.KeyModule.provide()).put(FindPeopleViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, FindPeopleViewModel_HiltModules.KeyModule.provide()).put(FollowListViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, FollowListViewModel_HiltModules.KeyModule.provide()).put(HashtagFeedViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, HashtagFeedViewModel_HiltModules.KeyModule.provide()).put(LikesViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, LikesViewModel_HiltModules.KeyModule.provide()).put(MainTabViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, MainTabViewModel_HiltModules.KeyModule.provide()).put(MessageThreadViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, MessageThreadViewModel_HiltModules.KeyModule.provide()).put(MutedUsersViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, MutedUsersViewModel_HiltModules.KeyModule.provide()).put(NotificationsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, NotificationsViewModel_HiltModules.KeyModule.provide()).put(OtherProfileViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, OtherProfileViewModel_HiltModules.KeyModule.provide()).put(PostDetailViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, PostDetailViewModel_HiltModules.KeyModule.provide()).put(ProfileViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, ProfileViewModel_HiltModules.KeyModule.provide()).put(SocialSetupViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, SocialSetupViewModel_HiltModules.KeyModule.provide()).put(SongDetailViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, SongDetailViewModel_HiltModules.KeyModule.provide()).put(SuggestedUsersListViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, SuggestedUsersListViewModel_HiltModules.KeyModule.provide()).put(ThreadListViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, ThreadListViewModel_HiltModules.KeyModule.provide()).build());
    }

    @Override
    public ViewModelComponentBuilder getViewModelComponentBuilder() {
      return new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public FragmentComponentBuilder fragmentComponentBuilder() {
      return new FragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @Override
    public ViewComponentBuilder viewComponentBuilder() {
      return new ViewCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @Override
    public void injectMainActivity(MainActivity arg0) {
    }
  }

  private static final class ViewModelCImpl extends CorusApplication_HiltComponents.ViewModelC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    private Provider<AppearanceSettingsViewModel> appearanceSettingsViewModelProvider;

    private Provider<AuthViewModel> authViewModelProvider;

    private Provider<BlockedUsersViewModel> blockedUsersViewModelProvider;

    private Provider<BotListViewModel> botListViewModelProvider;

    private Provider<ChangePhoneNumberViewModel> changePhoneNumberViewModelProvider;

    private Provider<ChangeUsernameViewModel> changeUsernameViewModelProvider;

    private Provider<CommentsViewModel> commentsViewModelProvider;

    private Provider<ComposeViewModel> composeViewModelProvider;

    private Provider<ContactFriendsListViewModel> contactFriendsListViewModelProvider;

    private Provider<CymbalClubViewModel> cymbalClubViewModelProvider;

    private Provider<EditCaptionViewModel> editCaptionViewModelProvider;

    private Provider<EditProfileViewModel> editProfileViewModelProvider;

    private Provider<ExploreViewModel> exploreViewModelProvider;

    private Provider<FeedViewModel> feedViewModelProvider;

    private Provider<FeedbackFormViewModel> feedbackFormViewModelProvider;

    private Provider<FilmDetailViewModel> filmDetailViewModelProvider;

    private Provider<FindPeopleViewModel> findPeopleViewModelProvider;

    private Provider<FollowListViewModel> followListViewModelProvider;

    private Provider<HashtagFeedViewModel> hashtagFeedViewModelProvider;

    private Provider<LikesViewModel> likesViewModelProvider;

    private Provider<MainTabViewModel> mainTabViewModelProvider;

    private Provider<MessageThreadViewModel> messageThreadViewModelProvider;

    private Provider<MutedUsersViewModel> mutedUsersViewModelProvider;

    private Provider<NotificationsViewModel> notificationsViewModelProvider;

    private Provider<OtherProfileViewModel> otherProfileViewModelProvider;

    private Provider<PostDetailViewModel> postDetailViewModelProvider;

    private Provider<ProfileViewModel> profileViewModelProvider;

    private Provider<SocialSetupViewModel> socialSetupViewModelProvider;

    private Provider<SongDetailViewModel> songDetailViewModelProvider;

    private Provider<SuggestedUsersListViewModel> suggestedUsersListViewModelProvider;

    private Provider<ThreadListViewModel> threadListViewModelProvider;

    private ViewModelCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, SavedStateHandle savedStateHandleParam,
        ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;

      initialize(savedStateHandleParam, viewModelLifecycleParam);
      initialize2(savedStateHandleParam, viewModelLifecycleParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.appearanceSettingsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
      this.authViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 1);
      this.blockedUsersViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 2);
      this.botListViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 3);
      this.changePhoneNumberViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 4);
      this.changeUsernameViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 5);
      this.commentsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 6);
      this.composeViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 7);
      this.contactFriendsListViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 8);
      this.cymbalClubViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 9);
      this.editCaptionViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 10);
      this.editProfileViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 11);
      this.exploreViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 12);
      this.feedViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 13);
      this.feedbackFormViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 14);
      this.filmDetailViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 15);
      this.findPeopleViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 16);
      this.followListViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 17);
      this.hashtagFeedViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 18);
      this.likesViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 19);
      this.mainTabViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 20);
      this.messageThreadViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 21);
      this.mutedUsersViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 22);
      this.notificationsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 23);
      this.otherProfileViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 24);
    }

    @SuppressWarnings("unchecked")
    private void initialize2(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.postDetailViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 25);
      this.profileViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 26);
      this.socialSetupViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 27);
      this.songDetailViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 28);
      this.suggestedUsersListViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 29);
      this.threadListViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 30);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(ImmutableMap.<String, javax.inject.Provider<ViewModel>>builderWithExpectedSize(31).put(AppearanceSettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) appearanceSettingsViewModelProvider)).put(AuthViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) authViewModelProvider)).put(BlockedUsersViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) blockedUsersViewModelProvider)).put(BotListViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) botListViewModelProvider)).put(ChangePhoneNumberViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) changePhoneNumberViewModelProvider)).put(ChangeUsernameViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) changeUsernameViewModelProvider)).put(CommentsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) commentsViewModelProvider)).put(ComposeViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) composeViewModelProvider)).put(ContactFriendsListViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) contactFriendsListViewModelProvider)).put(CymbalClubViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) cymbalClubViewModelProvider)).put(EditCaptionViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) editCaptionViewModelProvider)).put(EditProfileViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) editProfileViewModelProvider)).put(ExploreViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) exploreViewModelProvider)).put(FeedViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) feedViewModelProvider)).put(FeedbackFormViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) feedbackFormViewModelProvider)).put(FilmDetailViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) filmDetailViewModelProvider)).put(FindPeopleViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) findPeopleViewModelProvider)).put(FollowListViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) followListViewModelProvider)).put(HashtagFeedViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) hashtagFeedViewModelProvider)).put(LikesViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) likesViewModelProvider)).put(MainTabViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) mainTabViewModelProvider)).put(MessageThreadViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) messageThreadViewModelProvider)).put(MutedUsersViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) mutedUsersViewModelProvider)).put(NotificationsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) notificationsViewModelProvider)).put(OtherProfileViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) otherProfileViewModelProvider)).put(PostDetailViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) postDetailViewModelProvider)).put(ProfileViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) profileViewModelProvider)).put(SocialSetupViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) socialSetupViewModelProvider)).put(SongDetailViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) songDetailViewModelProvider)).put(SuggestedUsersListViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) suggestedUsersListViewModelProvider)).put(ThreadListViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) threadListViewModelProvider)).build());
    }

    @Override
    public Map<Class<?>, Object> getHiltViewModelAssistedMap() {
      return ImmutableMap.<Class<?>, Object>of();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final ViewModelCImpl viewModelCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          ViewModelCImpl viewModelCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.viewModelCImpl = viewModelCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // fm.corus.android.ui.screens.settings.AppearanceSettingsViewModel 
          return (T) new AppearanceSettingsViewModel(singletonCImpl.preferencesDataStoreProvider.get());

          case 1: // fm.corus.android.ui.screens.auth.AuthViewModel 
          return (T) new AuthViewModel(singletonCImpl.authRepositoryProvider.get(), singletonCImpl.userRepositoryProvider.get(), singletonCImpl.remoteConfigServiceProvider.get(), singletonCImpl.analyticsServiceProvider.get(), singletonCImpl.provideFirebaseAuthProvider.get());

          case 2: // fm.corus.android.ui.screens.settings.BlockedUsersViewModel 
          return (T) new BlockedUsersViewModel(singletonCImpl.authRepositoryProvider.get(), singletonCImpl.userRepositoryProvider.get());

          case 3: // fm.corus.android.ui.screens.findpeople.BotListViewModel 
          return (T) new BotListViewModel(singletonCImpl.cloudFunctionsDataSourceProvider.get(), singletonCImpl.authRepositoryProvider.get(), singletonCImpl.userRepositoryProvider.get());

          case 4: // fm.corus.android.ui.screens.settings.ChangePhoneNumberViewModel 
          return (T) new ChangePhoneNumberViewModel(singletonCImpl.provideFirebaseAuthProvider.get(), singletonCImpl.userRepositoryProvider.get());

          case 5: // fm.corus.android.ui.screens.settings.ChangeUsernameViewModel 
          return (T) new ChangeUsernameViewModel(singletonCImpl.authRepositoryProvider.get(), singletonCImpl.userRepositoryProvider.get());

          case 6: // fm.corus.android.ui.screens.feed.CommentsViewModel 
          return (T) new CommentsViewModel(singletonCImpl.postRepositoryProvider.get(), singletonCImpl.authRepositoryProvider.get(), singletonCImpl.userRepositoryProvider.get(), singletonCImpl.postEngagementManagerProvider.get());

          case 7: // fm.corus.android.ui.screens.compose.ComposeViewModel 
          return (T) new ComposeViewModel(singletonCImpl.postRepositoryProvider.get(), singletonCImpl.spotifyRepositoryProvider.get(), singletonCImpl.tMDBRepositoryProvider.get(), singletonCImpl.authRepositoryProvider.get(), singletonCImpl.analyticsServiceProvider.get(), singletonCImpl.userRepositoryProvider.get(), singletonCImpl.subscriptionRepositoryProvider.get());

          case 8: // fm.corus.android.ui.screens.findpeople.ContactFriendsListViewModel 
          return (T) new ContactFriendsListViewModel(singletonCImpl.userRepositoryProvider.get(), singletonCImpl.authRepositoryProvider.get(), singletonCImpl.firestoreDataSourceProvider.get());

          case 9: // fm.corus.android.ui.screens.subscription.CymbalClubViewModel 
          return (T) new CymbalClubViewModel(singletonCImpl.subscriptionRepositoryProvider.get());

          case 10: // fm.corus.android.ui.screens.feed.EditCaptionViewModel 
          return (T) new EditCaptionViewModel(singletonCImpl.postRepositoryProvider.get(), singletonCImpl.userRepositoryProvider.get());

          case 11: // fm.corus.android.ui.screens.profile.EditProfileViewModel 
          return (T) new EditProfileViewModel(singletonCImpl.userRepositoryProvider.get(), singletonCImpl.authRepositoryProvider.get(), singletonCImpl.postRepositoryProvider.get(), singletonCImpl.subscriptionRepositoryProvider.get());

          case 12: // fm.corus.android.ui.screens.explore.ExploreViewModel 
          return (T) new ExploreViewModel(singletonCImpl.exploreRepositoryProvider.get(), singletonCImpl.userRepositoryProvider.get(), singletonCImpl.spotifyRepositoryProvider.get(), singletonCImpl.tMDBRepositoryProvider.get(), singletonCImpl.remoteConfigServiceProvider.get(), singletonCImpl.provideFirebaseAuthProvider.get());

          case 13: // fm.corus.android.ui.screens.feed.FeedViewModel 
          return (T) new FeedViewModel(singletonCImpl.postRepositoryProvider.get(), singletonCImpl.authRepositoryProvider.get(), singletonCImpl.postEngagementManagerProvider.get(), singletonCImpl.userRepositoryProvider.get(), singletonCImpl.messageRepositoryProvider.get(), singletonCImpl.nowPlayingManagerProvider.get(), singletonCImpl.remoteConfigServiceProvider.get());

          case 14: // fm.corus.android.ui.screens.settings.FeedbackFormViewModel 
          return (T) new FeedbackFormViewModel(singletonCImpl.authRepositoryProvider.get(), singletonCImpl.userRepositoryProvider.get());

          case 15: // fm.corus.android.ui.screens.feed.FilmDetailViewModel 
          return (T) new FilmDetailViewModel(singletonCImpl.postRepositoryProvider.get());

          case 16: // fm.corus.android.ui.screens.findpeople.FindPeopleViewModel 
          return (T) new FindPeopleViewModel(singletonCImpl.userRepositoryProvider.get(), singletonCImpl.authRepositoryProvider.get(), singletonCImpl.exploreRepositoryProvider.get(), singletonCImpl.cloudFunctionsDataSourceProvider.get(), singletonCImpl.spotifyRepositoryProvider.get(), singletonCImpl.preferencesDataStoreProvider.get(), singletonCImpl.firestoreDataSourceProvider.get());

          case 17: // fm.corus.android.ui.screens.profile.FollowListViewModel 
          return (T) new FollowListViewModel(singletonCImpl.userRepositoryProvider.get(), singletonCImpl.authRepositoryProvider.get(), singletonCImpl.cloudFunctionsDataSourceProvider.get());

          case 18: // fm.corus.android.ui.screens.explore.HashtagFeedViewModel 
          return (T) new HashtagFeedViewModel(singletonCImpl.postRepositoryProvider.get(), singletonCImpl.authRepositoryProvider.get());

          case 19: // fm.corus.android.ui.screens.feed.LikesViewModel 
          return (T) new LikesViewModel(singletonCImpl.postRepositoryProvider.get(), singletonCImpl.userRepositoryProvider.get(), singletonCImpl.authRepositoryProvider.get());

          case 20: // fm.corus.android.ui.navigation.MainTabViewModel 
          return (T) new MainTabViewModel(singletonCImpl.nowPlayingManagerProvider.get(), singletonCImpl.subscriptionRepositoryProvider.get(), singletonCImpl.preferencesDataStoreProvider.get());

          case 21: // fm.corus.android.ui.screens.messaging.MessageThreadViewModel 
          return (T) new MessageThreadViewModel(singletonCImpl.messageRepositoryProvider.get(), singletonCImpl.authRepositoryProvider.get(), singletonCImpl.userRepositoryProvider.get());

          case 22: // fm.corus.android.ui.screens.settings.MutedUsersViewModel 
          return (T) new MutedUsersViewModel(singletonCImpl.authRepositoryProvider.get(), singletonCImpl.userRepositoryProvider.get());

          case 23: // fm.corus.android.ui.screens.notifications.NotificationsViewModel 
          return (T) new NotificationsViewModel(singletonCImpl.notificationRepositoryProvider.get(), singletonCImpl.authRepositoryProvider.get());

          case 24: // fm.corus.android.ui.screens.profile.OtherProfileViewModel 
          return (T) new OtherProfileViewModel(singletonCImpl.userRepositoryProvider.get(), singletonCImpl.postRepositoryProvider.get(), singletonCImpl.authRepositoryProvider.get(), singletonCImpl.nowPlayingManagerProvider.get());

          case 25: // fm.corus.android.ui.screens.feed.PostDetailViewModel 
          return (T) new PostDetailViewModel(singletonCImpl.postRepositoryProvider.get(), singletonCImpl.authRepositoryProvider.get(), singletonCImpl.userRepositoryProvider.get(), singletonCImpl.postEngagementManagerProvider.get());

          case 26: // fm.corus.android.ui.screens.profile.ProfileViewModel 
          return (T) new ProfileViewModel(singletonCImpl.authRepositoryProvider.get(), singletonCImpl.cloudFunctionsDataSourceProvider.get(), singletonCImpl.userRepositoryProvider.get(), singletonCImpl.subscriptionRepositoryProvider.get(), singletonCImpl.nowPlayingManagerProvider.get());

          case 27: // fm.corus.android.ui.screens.auth.SocialSetupViewModel 
          return (T) new SocialSetupViewModel(singletonCImpl.authRepositoryProvider.get(), singletonCImpl.userRepositoryProvider.get(), singletonCImpl.postRepositoryProvider.get(), singletonCImpl.cloudFunctionsDataSourceProvider.get(), singletonCImpl.firestoreDataSourceProvider.get(), singletonCImpl.nowPlayingManagerProvider.get());

          case 28: // fm.corus.android.ui.screens.feed.SongDetailViewModel 
          return (T) new SongDetailViewModel(singletonCImpl.postRepositoryProvider.get(), singletonCImpl.nowPlayingManagerProvider.get());

          case 29: // fm.corus.android.ui.screens.findpeople.SuggestedUsersListViewModel 
          return (T) new SuggestedUsersListViewModel(singletonCImpl.userRepositoryProvider.get(), singletonCImpl.authRepositoryProvider.get());

          case 30: // fm.corus.android.ui.screens.messaging.ThreadListViewModel 
          return (T) new ThreadListViewModel(singletonCImpl.messageRepositoryProvider.get(), singletonCImpl.authRepositoryProvider.get(), singletonCImpl.userRepositoryProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends CorusApplication_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    private Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    private ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
        SavedStateHandleHolder savedStateHandleHolderParam) {
      this.singletonCImpl = singletonCImpl;

      initialize(savedStateHandleHolderParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandleHolder savedStateHandleHolderParam) {
      this.provideActivityRetainedLifecycleProvider = DoubleCheck.provider(new SwitchingProvider<ActivityRetainedLifecycle>(singletonCImpl, activityRetainedCImpl, 0));
    }

    @Override
    public ActivityComponentBuilder activityComponentBuilder() {
      return new ActivityCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public ActivityRetainedLifecycle getActivityRetainedLifecycle() {
      return provideActivityRetainedLifecycleProvider.get();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // dagger.hilt.android.ActivityRetainedLifecycle 
          return (T) ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory.provideActivityRetainedLifecycle();

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ServiceCImpl extends CorusApplication_HiltComponents.ServiceC {
    private final SingletonCImpl singletonCImpl;

    private final ServiceCImpl serviceCImpl = this;

    private ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
      this.singletonCImpl = singletonCImpl;


    }
  }

  private static final class SingletonCImpl extends CorusApplication_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    private Provider<DataStore<Preferences>> provideDataStoreProvider;

    private Provider<PreferencesDataStore> preferencesDataStoreProvider;

    private Provider<FirebaseAuth> provideFirebaseAuthProvider;

    private Provider<FirebaseFirestore> provideFirestoreProvider;

    private Provider<FirestoreDataSource> firestoreDataSourceProvider;

    private Provider<FirebaseStorage> provideFirebaseStorageProvider;

    private Provider<FirebaseStorageDataSource> firebaseStorageDataSourceProvider;

    private Provider<FirebaseMessaging> provideFirebaseMessagingProvider;

    private Provider<FirebaseFunctions> provideFirebaseFunctionsProvider;

    private Provider<CloudFunctionsDataSource> cloudFunctionsDataSourceProvider;

    private Provider<AuthRepository> authRepositoryProvider;

    private Provider<UserRepository> userRepositoryProvider;

    private Provider<FirebaseRemoteConfig> provideRemoteConfigProvider;

    private Provider<RemoteConfigService> remoteConfigServiceProvider;

    private Provider<FirebaseAnalytics> provideFirebaseAnalyticsProvider;

    private Provider<AnalyticsService> analyticsServiceProvider;

    private Provider<PostRepository> postRepositoryProvider;

    private Provider<PostEngagementManager> postEngagementManagerProvider;

    private Provider<SpotifyRepository> spotifyRepositoryProvider;

    private Provider<Json> provideJsonProvider;

    private Provider<HttpClient> provideHttpClientProvider;

    private Provider<TMDBApiService> tMDBApiServiceProvider;

    private Provider<TMDBRepository> tMDBRepositoryProvider;

    private Provider<SubscriptionRepository> subscriptionRepositoryProvider;

    private Provider<ExploreRepository> exploreRepositoryProvider;

    private Provider<MessageRepository> messageRepositoryProvider;

    private Provider<NowPlayingManager> nowPlayingManagerProvider;

    private Provider<NotificationRepository> notificationRepositoryProvider;

    private SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);
      initialize2(applicationContextModuleParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.provideDataStoreProvider = DoubleCheck.provider(new SwitchingProvider<DataStore<Preferences>>(singletonCImpl, 1));
      this.preferencesDataStoreProvider = DoubleCheck.provider(new SwitchingProvider<PreferencesDataStore>(singletonCImpl, 0));
      this.provideFirebaseAuthProvider = DoubleCheck.provider(new SwitchingProvider<FirebaseAuth>(singletonCImpl, 3));
      this.provideFirestoreProvider = DoubleCheck.provider(new SwitchingProvider<FirebaseFirestore>(singletonCImpl, 5));
      this.firestoreDataSourceProvider = DoubleCheck.provider(new SwitchingProvider<FirestoreDataSource>(singletonCImpl, 4));
      this.provideFirebaseStorageProvider = DoubleCheck.provider(new SwitchingProvider<FirebaseStorage>(singletonCImpl, 7));
      this.firebaseStorageDataSourceProvider = DoubleCheck.provider(new SwitchingProvider<FirebaseStorageDataSource>(singletonCImpl, 6));
      this.provideFirebaseMessagingProvider = DoubleCheck.provider(new SwitchingProvider<FirebaseMessaging>(singletonCImpl, 8));
      this.provideFirebaseFunctionsProvider = DoubleCheck.provider(new SwitchingProvider<FirebaseFunctions>(singletonCImpl, 10));
      this.cloudFunctionsDataSourceProvider = DoubleCheck.provider(new SwitchingProvider<CloudFunctionsDataSource>(singletonCImpl, 9));
      this.authRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<AuthRepository>(singletonCImpl, 2));
      this.userRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<UserRepository>(singletonCImpl, 11));
      this.provideRemoteConfigProvider = DoubleCheck.provider(new SwitchingProvider<FirebaseRemoteConfig>(singletonCImpl, 13));
      this.remoteConfigServiceProvider = DoubleCheck.provider(new SwitchingProvider<RemoteConfigService>(singletonCImpl, 12));
      this.provideFirebaseAnalyticsProvider = DoubleCheck.provider(new SwitchingProvider<FirebaseAnalytics>(singletonCImpl, 15));
      this.analyticsServiceProvider = DoubleCheck.provider(new SwitchingProvider<AnalyticsService>(singletonCImpl, 14));
      this.postRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<PostRepository>(singletonCImpl, 16));
      this.postEngagementManagerProvider = DoubleCheck.provider(new SwitchingProvider<PostEngagementManager>(singletonCImpl, 17));
      this.spotifyRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<SpotifyRepository>(singletonCImpl, 18));
      this.provideJsonProvider = DoubleCheck.provider(new SwitchingProvider<Json>(singletonCImpl, 22));
      this.provideHttpClientProvider = DoubleCheck.provider(new SwitchingProvider<HttpClient>(singletonCImpl, 21));
      this.tMDBApiServiceProvider = DoubleCheck.provider(new SwitchingProvider<TMDBApiService>(singletonCImpl, 20));
      this.tMDBRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<TMDBRepository>(singletonCImpl, 19));
      this.subscriptionRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<SubscriptionRepository>(singletonCImpl, 23));
      this.exploreRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<ExploreRepository>(singletonCImpl, 24));
    }

    @SuppressWarnings("unchecked")
    private void initialize2(final ApplicationContextModule applicationContextModuleParam) {
      this.messageRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<MessageRepository>(singletonCImpl, 25));
      this.nowPlayingManagerProvider = DoubleCheck.provider(new SwitchingProvider<NowPlayingManager>(singletonCImpl, 26));
      this.notificationRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<NotificationRepository>(singletonCImpl, 27));
    }

    @Override
    public Set<Boolean> getDisableFragmentGetContextFix() {
      return ImmutableSet.<Boolean>of();
    }

    @Override
    public ActivityRetainedComponentBuilder retainedComponentBuilder() {
      return new ActivityRetainedCBuilder(singletonCImpl);
    }

    @Override
    public ServiceComponentBuilder serviceComponentBuilder() {
      return new ServiceCBuilder(singletonCImpl);
    }

    @Override
    public void injectCorusApplication(CorusApplication arg0) {
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // fm.corus.android.data.local.PreferencesDataStore 
          return (T) new PreferencesDataStore(singletonCImpl.provideDataStoreProvider.get());

          case 1: // androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> 
          return (T) AppModule_ProvideDataStoreFactory.provideDataStore(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 2: // fm.corus.android.data.repository.AuthRepository 
          return (T) new AuthRepository(singletonCImpl.provideFirebaseAuthProvider.get(), singletonCImpl.firestoreDataSourceProvider.get(), singletonCImpl.firebaseStorageDataSourceProvider.get(), singletonCImpl.provideFirebaseMessagingProvider.get(), singletonCImpl.cloudFunctionsDataSourceProvider.get());

          case 3: // com.google.firebase.auth.FirebaseAuth 
          return (T) AppModule_ProvideFirebaseAuthFactory.provideFirebaseAuth();

          case 4: // fm.corus.android.data.remote.FirestoreDataSource 
          return (T) new FirestoreDataSource(singletonCImpl.provideFirestoreProvider.get());

          case 5: // com.google.firebase.firestore.FirebaseFirestore 
          return (T) AppModule_ProvideFirestoreFactory.provideFirestore();

          case 6: // fm.corus.android.data.remote.FirebaseStorageDataSource 
          return (T) new FirebaseStorageDataSource(singletonCImpl.provideFirebaseStorageProvider.get());

          case 7: // com.google.firebase.storage.FirebaseStorage 
          return (T) AppModule_ProvideFirebaseStorageFactory.provideFirebaseStorage();

          case 8: // com.google.firebase.messaging.FirebaseMessaging 
          return (T) AppModule_ProvideFirebaseMessagingFactory.provideFirebaseMessaging();

          case 9: // fm.corus.android.data.remote.CloudFunctionsDataSource 
          return (T) new CloudFunctionsDataSource(singletonCImpl.provideFirebaseFunctionsProvider.get());

          case 10: // com.google.firebase.functions.FirebaseFunctions 
          return (T) AppModule_ProvideFirebaseFunctionsFactory.provideFirebaseFunctions();

          case 11: // fm.corus.android.data.repository.UserRepository 
          return (T) new UserRepository(singletonCImpl.firestoreDataSourceProvider.get(), singletonCImpl.firebaseStorageDataSourceProvider.get(), singletonCImpl.cloudFunctionsDataSourceProvider.get(), singletonCImpl.preferencesDataStoreProvider.get());

          case 12: // fm.corus.android.service.RemoteConfigService 
          return (T) new RemoteConfigService(singletonCImpl.provideRemoteConfigProvider.get());

          case 13: // com.google.firebase.remoteconfig.FirebaseRemoteConfig 
          return (T) AppModule_ProvideRemoteConfigFactory.provideRemoteConfig();

          case 14: // fm.corus.android.service.AnalyticsService 
          return (T) new AnalyticsService(singletonCImpl.provideFirebaseAnalyticsProvider.get());

          case 15: // com.google.firebase.analytics.FirebaseAnalytics 
          return (T) RepositoryModule_ProvideFirebaseAnalyticsFactory.provideFirebaseAnalytics(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 16: // fm.corus.android.data.repository.PostRepository 
          return (T) new PostRepository(singletonCImpl.cloudFunctionsDataSourceProvider.get(), singletonCImpl.firestoreDataSourceProvider.get(), singletonCImpl.firebaseStorageDataSourceProvider.get());

          case 17: // fm.corus.android.domain.PostEngagementManager 
          return (T) new PostEngagementManager(singletonCImpl.postRepositoryProvider.get());

          case 18: // fm.corus.android.data.repository.SpotifyRepository 
          return (T) new SpotifyRepository(singletonCImpl.cloudFunctionsDataSourceProvider.get());

          case 19: // fm.corus.android.data.repository.TMDBRepository 
          return (T) new TMDBRepository(singletonCImpl.tMDBApiServiceProvider.get());

          case 20: // fm.corus.android.data.remote.TMDBApiService 
          return (T) new TMDBApiService(singletonCImpl.provideHttpClientProvider.get());

          case 21: // io.ktor.client.HttpClient 
          return (T) NetworkModule_ProvideHttpClientFactory.provideHttpClient(singletonCImpl.provideJsonProvider.get());

          case 22: // kotlinx.serialization.json.Json 
          return (T) NetworkModule_ProvideJsonFactory.provideJson();

          case 23: // fm.corus.android.data.repository.SubscriptionRepository 
          return (T) new SubscriptionRepository(singletonCImpl.cloudFunctionsDataSourceProvider.get(), singletonCImpl.remoteConfigServiceProvider.get());

          case 24: // fm.corus.android.data.repository.ExploreRepository 
          return (T) new ExploreRepository(singletonCImpl.firestoreDataSourceProvider.get());

          case 25: // fm.corus.android.data.repository.MessageRepository 
          return (T) new MessageRepository(singletonCImpl.cloudFunctionsDataSourceProvider.get(), singletonCImpl.firebaseStorageDataSourceProvider.get());

          case 26: // fm.corus.android.domain.NowPlayingManager 
          return (T) new NowPlayingManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.cloudFunctionsDataSourceProvider.get());

          case 27: // fm.corus.android.data.repository.NotificationRepository 
          return (T) new NotificationRepository(singletonCImpl.cloudFunctionsDataSourceProvider.get(), singletonCImpl.firestoreDataSourceProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }
}
