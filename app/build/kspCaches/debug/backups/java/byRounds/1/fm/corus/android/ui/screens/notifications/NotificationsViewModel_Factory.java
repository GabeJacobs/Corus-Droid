package fm.corus.android.ui.screens.notifications;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.repository.AuthRepository;
import fm.corus.android.data.repository.NotificationRepository;
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
public final class NotificationsViewModel_Factory implements Factory<NotificationsViewModel> {
  private final Provider<NotificationRepository> notificationRepositoryProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  public NotificationsViewModel_Factory(
      Provider<NotificationRepository> notificationRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider) {
    this.notificationRepositoryProvider = notificationRepositoryProvider;
    this.authRepositoryProvider = authRepositoryProvider;
  }

  @Override
  public NotificationsViewModel get() {
    return newInstance(notificationRepositoryProvider.get(), authRepositoryProvider.get());
  }

  public static NotificationsViewModel_Factory create(
      Provider<NotificationRepository> notificationRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider) {
    return new NotificationsViewModel_Factory(notificationRepositoryProvider, authRepositoryProvider);
  }

  public static NotificationsViewModel newInstance(NotificationRepository notificationRepository,
      AuthRepository authRepository) {
    return new NotificationsViewModel(notificationRepository, authRepository);
  }
}
