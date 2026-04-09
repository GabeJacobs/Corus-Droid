package fm.corus.android.service;

import com.google.firebase.analytics.FirebaseAnalytics;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
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
public final class AnalyticsService_Factory implements Factory<AnalyticsService> {
  private final Provider<FirebaseAnalytics> analyticsProvider;

  public AnalyticsService_Factory(Provider<FirebaseAnalytics> analyticsProvider) {
    this.analyticsProvider = analyticsProvider;
  }

  @Override
  public AnalyticsService get() {
    return newInstance(analyticsProvider.get());
  }

  public static AnalyticsService_Factory create(Provider<FirebaseAnalytics> analyticsProvider) {
    return new AnalyticsService_Factory(analyticsProvider);
  }

  public static AnalyticsService newInstance(FirebaseAnalytics analytics) {
    return new AnalyticsService(analytics);
  }
}
