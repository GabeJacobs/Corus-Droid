package fm.corus.android.ui.screens.messaging;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fm.corus.android.data.repository.AuthRepository;
import fm.corus.android.data.repository.MessageRepository;
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
public final class MessageThreadViewModel_Factory implements Factory<MessageThreadViewModel> {
  private final Provider<MessageRepository> messageRepositoryProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  private final Provider<UserRepository> userRepositoryProvider;

  public MessageThreadViewModel_Factory(Provider<MessageRepository> messageRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider) {
    this.messageRepositoryProvider = messageRepositoryProvider;
    this.authRepositoryProvider = authRepositoryProvider;
    this.userRepositoryProvider = userRepositoryProvider;
  }

  @Override
  public MessageThreadViewModel get() {
    return newInstance(messageRepositoryProvider.get(), authRepositoryProvider.get(), userRepositoryProvider.get());
  }

  public static MessageThreadViewModel_Factory create(
      Provider<MessageRepository> messageRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider,
      Provider<UserRepository> userRepositoryProvider) {
    return new MessageThreadViewModel_Factory(messageRepositoryProvider, authRepositoryProvider, userRepositoryProvider);
  }

  public static MessageThreadViewModel newInstance(MessageRepository messageRepository,
      AuthRepository authRepository, UserRepository userRepository) {
    return new MessageThreadViewModel(messageRepository, authRepository, userRepository);
  }
}
