package org.thoughtcrime.securesms.glide;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.UnitModelLoader;
import com.bumptech.glide.load.resource.bitmap.BitmapDrawableEncoder;
import com.bumptech.glide.load.resource.gif.ByteBufferGifDecoder;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.load.resource.gif.StreamGifDecoder;

import org.signal.glide.common.io.InputStreamFactory;
import org.signal.glide.load.resource.apng.decode.APNGDecoder;
import org.thoughtcrime.securesms.badges.load.BadgeLoader;
import org.thoughtcrime.securesms.badges.load.GiftBadgeModel;
import org.thoughtcrime.securesms.badges.models.Badge;
import org.thoughtcrime.securesms.blurhash.BlurHash;
import org.thoughtcrime.securesms.blurhash.BlurHashModelLoader;
import org.thoughtcrime.securesms.blurhash.BlurHashResourceDecoder;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhotoLoader;
import org.thoughtcrime.securesms.crypto.AttachmentSecret;
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider;
import org.thoughtcrime.securesms.giph.model.ChunkedImageUrl;
import org.thoughtcrime.securesms.glide.cache.ApngFrameDrawableTranscoder;
import org.thoughtcrime.securesms.glide.cache.ByteBufferApngDecoder;
import org.thoughtcrime.securesms.glide.cache.EncryptedApngCacheEncoder;
import org.thoughtcrime.securesms.glide.cache.EncryptedBitmapResourceEncoder;
import org.thoughtcrime.securesms.glide.cache.EncryptedCacheDecoder;
import org.thoughtcrime.securesms.glide.cache.EncryptedCacheEncoder;
import org.thoughtcrime.securesms.glide.cache.EncryptedGifDrawableResourceEncoder;
import org.thoughtcrime.securesms.glide.cache.InputStreamFactoryBitmapDecoder;
import org.thoughtcrime.securesms.glide.cache.StreamApngDecoder;
import org.thoughtcrime.securesms.glide.cache.StreamBitmapDecoder;
import org.thoughtcrime.securesms.glide.cache.StreamFactoryApngDecoder;
import org.thoughtcrime.securesms.glide.cache.StreamFactoryGifDecoder;
import org.thoughtcrime.securesms.glide.cache.WebpSanDecoder;
import org.thoughtcrime.securesms.mms.DecryptableUri;
import org.thoughtcrime.securesms.mms.DecryptableUriStreamLoader;
import org.thoughtcrime.securesms.mms.RegisterGlideComponents;
import org.thoughtcrime.securesms.mms.SignalGlideModule;
import org.thoughtcrime.securesms.stickers.StickerRemoteUri;
import org.thoughtcrime.securesms.stickers.StickerRemoteUriLoader;
import org.thoughtcrime.securesms.stories.StoryTextPostModel;
import org.thoughtcrime.securesms.util.ConversationShortcutPhoto;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * The core logic for {@link SignalGlideModule}. This is a separate class because it uses
 * dependencies defined in the main Gradle module.
 */
public class SignalGlideComponents implements RegisterGlideComponents {

  @Override
  public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
    AttachmentSecret attachmentSecret = AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret();
    byte[]           secret           = attachmentSecret.getModernKey();

    registry.prepend(File.class, File.class, UnitModelLoader.Factory.getInstance());

    registry.prepend(InputStream.class, Bitmap.class, new WebpSanDecoder());

    registry.prepend(InputStream.class, new EncryptedCacheEncoder(secret, glide.getArrayPool()));

    registry.prepend(File.class, Bitmap.class, new EncryptedCacheDecoder<>(secret, new StreamBitmapDecoder(context, glide, registry)));

    StreamGifDecoder        streamGifDecoder        = new StreamGifDecoder(registry.getImageHeaderParsers(), new ByteBufferGifDecoder(context, registry.getImageHeaderParsers(), glide.getBitmapPool(), glide.getArrayPool()), glide.getArrayPool());
    StreamFactoryGifDecoder streamFactoryGifDecoder = new StreamFactoryGifDecoder(streamGifDecoder);
    registry.prepend(InputStream.class, GifDrawable.class, streamGifDecoder);
    registry.prepend(InputStreamFactory.class, GifDrawable.class, streamFactoryGifDecoder);
    registry.prepend(GifDrawable.class, new EncryptedGifDrawableResourceEncoder(secret));
    registry.prepend(File.class, GifDrawable.class, new EncryptedCacheDecoder<>(secret, streamGifDecoder));

    EncryptedBitmapResourceEncoder encryptedBitmapResourceEncoder = new EncryptedBitmapResourceEncoder(secret);
    registry.prepend(Bitmap.class, new EncryptedBitmapResourceEncoder(secret));
    registry.prepend(BitmapDrawable.class, new BitmapDrawableEncoder(glide.getBitmapPool(), encryptedBitmapResourceEncoder));

    ByteBufferApngDecoder    byteBufferApngDecoder    = new ByteBufferApngDecoder();
    StreamApngDecoder        streamApngDecoder        = new StreamApngDecoder(byteBufferApngDecoder);
    StreamFactoryApngDecoder streamFactoryApngDecoder = new StreamFactoryApngDecoder(byteBufferApngDecoder, glide, registry);

    registry.prepend(InputStream.class, APNGDecoder.class, streamApngDecoder);
    registry.prepend(InputStreamFactory.class, APNGDecoder.class, streamFactoryApngDecoder);
    registry.prepend(ByteBuffer.class, APNGDecoder.class, byteBufferApngDecoder);
    registry.prepend(APNGDecoder.class, new EncryptedApngCacheEncoder(secret));
    registry.prepend(File.class, APNGDecoder.class, new EncryptedCacheDecoder<>(secret, streamApngDecoder));
    registry.register(APNGDecoder.class, Drawable.class, new ApngFrameDrawableTranscoder());

    registry.prepend(BlurHash.class, Bitmap.class, new BlurHashResourceDecoder());
    registry.prepend(StoryTextPostModel.class, Bitmap.class, new StoryTextPostModel.Decoder());

    registry.append(StoryTextPostModel.class, StoryTextPostModel.class, UnitModelLoader.Factory.getInstance());
    registry.append(ConversationShortcutPhoto.class, Bitmap.class, new ConversationShortcutPhoto.Loader.Factory(context));
    registry.append(ContactPhoto.class, InputStream.class, new ContactPhotoLoader.Factory(context));
    registry.append(DecryptableUri.class, InputStreamFactory.class, new DecryptableUriStreamLoader.Factory(context));
    registry.append(InputStreamFactory.class, Bitmap.class, new InputStreamFactoryBitmapDecoder(context, glide, registry));
    registry.append(ChunkedImageUrl.class, InputStream.class, new ChunkedImageUrlLoader.Factory());
    registry.append(StickerRemoteUri.class, InputStream.class, new StickerRemoteUriLoader.Factory());
    registry.append(BlurHash.class, BlurHash.class, new BlurHashModelLoader.Factory());
    registry.append(Badge.class, InputStream.class, BadgeLoader.createFactory());
    registry.append(GiftBadgeModel.class, InputStream.class, GiftBadgeModel.createFactory());
    registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory());
  }
}
