package fm.corus.android.ui.components;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.repository.GifRepository;
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
public final class GifPickerViewModel_Factory implements Factory<GifPickerViewModel> {
  private final Provider<GifRepository> gifRepositoryProvider;

  public GifPickerViewModel_Factory(Provider<GifRepository> gifRepositoryProvider) {
    this.gifRepositoryProvider = gifRepositoryProvider;
  }

  @Override
  public GifPickerViewModel get() {
    return newInstance(gifRepositoryProvider.get());
  }

  public static GifPickerViewModel_Factory create(Provider<GifRepository> gifRepositoryProvider) {
    return new GifPickerViewModel_Factory(gifRepositoryProvider);
  }

  public static GifPickerViewModel newInstance(GifRepository gifRepository) {
    return new GifPickerViewModel(gifRepository);
  }
}
