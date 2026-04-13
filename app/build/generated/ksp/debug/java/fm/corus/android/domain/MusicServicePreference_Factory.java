package fm.corus.android.domain;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class MusicServicePreference_Factory implements Factory<MusicServicePreference> {
  private final Provider<Context> contextProvider;

  public MusicServicePreference_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public MusicServicePreference get() {
    return newInstance(contextProvider.get());
  }

  public static MusicServicePreference_Factory create(Provider<Context> contextProvider) {
    return new MusicServicePreference_Factory(contextProvider);
  }

  public static MusicServicePreference newInstance(Context context) {
    return new MusicServicePreference(context);
  }
}
