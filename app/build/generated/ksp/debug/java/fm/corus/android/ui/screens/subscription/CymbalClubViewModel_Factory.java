package fm.corus.android.ui.screens.subscription;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.repository.SubscriptionRepository;
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
public final class CymbalClubViewModel_Factory implements Factory<CymbalClubViewModel> {
  private final Provider<SubscriptionRepository> subscriptionRepositoryProvider;

  public CymbalClubViewModel_Factory(
      Provider<SubscriptionRepository> subscriptionRepositoryProvider) {
    this.subscriptionRepositoryProvider = subscriptionRepositoryProvider;
  }

  @Override
  public CymbalClubViewModel get() {
    return newInstance(subscriptionRepositoryProvider.get());
  }

  public static CymbalClubViewModel_Factory create(
      Provider<SubscriptionRepository> subscriptionRepositoryProvider) {
    return new CymbalClubViewModel_Factory(subscriptionRepositoryProvider);
  }

  public static CymbalClubViewModel newInstance(SubscriptionRepository subscriptionRepository) {
    return new CymbalClubViewModel(subscriptionRepository);
  }
}
