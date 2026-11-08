package com.tim.d2a;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Adler32;
import java.util.zip.DeflaterOutputStream;

public final class FixtureDictionaryProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "text/plain";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        byte[] contents;
        if ("large.mdx".equals(uri.getLastPathSegment())) {
            contents = makeLargeMdx();
        } else if ("first.mdx".equals(uri.getLastPathSegment())) {
            contents = makeMdx("First dictionary", "Definition from the first dictionary.");
        } else if ("second.mdx".equals(uri.getLastPathSegment())) {
            contents = makeMdx("Second dictionary", "Definition from the second dictionary.");
        } else if ("corrupt.mdx".equals(uri.getLastPathSegment())) {
            contents = makeMdx("Corrupt", "Definition");
            contents[8] ^= 1;
        } else if ("encrypted.mdx".equals(uri.getLastPathSegment())) {
            contents = makeMdx("Encrypted", "Definition");
        } else if ("bad.mdx".equals(uri.getLastPathSegment())) {
            contents = new byte[] {1, 2, 3};
        } else if ("accented.mdx".equals(uri.getLastPathSegment())) {
            contents = ("árbol\tA tree.\ncafé\tCoffee.\nhablar\tTo speak.\ntener\tTo have.\n").getBytes(StandardCharsets.UTF_8);
        } else if ("senses.mdx".equals(uri.getLastPathSegment())) {
            contents = ("banco\tA financial institution.\nbanco\tA bench for sitting.\n").getBytes(StandardCharsets.UTF_8);
        } else if ("utf16le.mdx".equals(uri.getLastPathSegment())) {
            contents = makeMdx("UTF16 LE", "A fortunate discovery.", "UTF-16LE");
        } else if ("utf16be.mdx".equals(uri.getLastPathSegment())) {
            contents = makeMdx("UTF16 BE", "A fortunate discovery.", "UTF-16BE");
        } else {
            throw new FileNotFoundException(uri.toString());
        }

        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            Thread writer = new Thread(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream output =
                             new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    output.write(contents);
                } catch (IOException ignored) {
                }
            });
            writer.start();
            return pipe[0];
        } catch (IOException error) {
            FileNotFoundException failure = new FileNotFoundException(error.getMessage());
            failure.initCause(error);
            throw failure;
        }
    }

    private static byte[] makeMdx(String title, String definition) throws FileNotFoundException {
        return makeMdx(title, definition, "UTF-8");
    }

    private static byte[] makeMdx(String title, String definition, String encoding) throws FileNotFoundException {
        try {
            java.nio.charset.Charset charset = java.nio.charset.Charset.forName(encoding);
            int unitWidth = encoding.startsWith("UTF-16") ? 2 : 1;
            ByteArrayOutputStream file = new ByteArrayOutputStream();
            byte[] header = ("<Dictionary Title=\"" + title + "\" Encoding=\"" + encoding + "\" " +
                    "GeneratedByEngineVersion=\"1.2\" Encrypted=\"" + (title.equals("Encrypted") ? "Yes" : "No") + "\">")
                    .getBytes(StandardCharsets.UTF_16LE);
            writeBigEndian(file, header.length, 4);
            file.write(header);
            Adler32 checksum = new Adler32();
            checksum.update(header);
            writeLittleEndianInt(file, (int) checksum.getValue());

            byte[] word = "serendipity".getBytes(charset);
            ByteArrayOutputStream keyData = new ByteArrayOutputStream();
            writeBigEndian(keyData, 0, 4);
            keyData.write(word);
            for (int i = 0; i < unitWidth; i++) keyData.write(0);
            byte[] keyBlock = rawBlock(keyData.toByteArray());

            ByteArrayOutputStream keyInfo = new ByteArrayOutputStream();
            writeBigEndian(keyInfo, 1, 4);
            keyInfo.write(word.length / unitWidth);
            keyInfo.write(word);
            keyInfo.write(word.length / unitWidth);
            keyInfo.write(word);
            writeBigEndian(keyInfo, keyBlock.length, 4);
            writeBigEndian(keyInfo, keyData.size(), 4);

            byte[] recordData = definition.getBytes(charset);
            byte[] recordBlock = rawBlock(recordData);
            writeBigEndian(file, 1, 4);
            writeBigEndian(file, 1, 4);
            writeBigEndian(file, keyInfo.size(), 4);
            writeBigEndian(file, keyBlock.length, 4);
            file.write(keyInfo.toByteArray());
            file.write(keyBlock);
            writeBigEndian(file, 1, 4);
            writeBigEndian(file, 1, 4);
            writeBigEndian(file, 8, 4);
            writeBigEndian(file, recordBlock.length, 4);
            writeBigEndian(file, recordBlock.length, 4);
            writeBigEndian(file, recordData.length, 4);
            file.write(recordBlock);
            return file.toByteArray();
        } catch (IOException error) {
            FileNotFoundException failure = new FileNotFoundException(error.getMessage());
            failure.initCause(error);
            throw failure;
        }
    }

    // Small compressed fixture expands to 160 MB, exceeding safe whole-record buffering.
    private static byte[] makeLargeMdx() throws FileNotFoundException {
        try {
            int count = 160;
            int recordSize = 1024 * 1024;
            ByteArrayOutputStream file = new ByteArrayOutputStream();
            byte[] header = "<Dictionary Title=\"Large dictionary\" Encoding=\"UTF-8\" GeneratedByEngineVersion=\"1.2\" Encrypted=\"No\">".getBytes(StandardCharsets.UTF_16LE);
            writeBigEndian(file, header.length, 4);
            file.write(header);
            Adler32 checksum = new Adler32();
            checksum.update(header);
            writeLittleEndianInt(file, (int) checksum.getValue());
            ByteArrayOutputStream keys = new ByteArrayOutputStream();
            for (int i = 0; i < count; i++) {
                writeBigEndian(keys, (long) i * recordSize, 4);
                keys.write(String.format(java.util.Locale.ROOT, "word%03d", i).getBytes(StandardCharsets.UTF_8));
                keys.write(0);
            }
            byte[] keyBlock = rawBlock(keys.toByteArray());
            ByteArrayOutputStream keyInfo = new ByteArrayOutputStream();
            writeBigEndian(keyInfo, count, 4);
            keyInfo.write(7); keyInfo.write("word000".getBytes(StandardCharsets.UTF_8));
            keyInfo.write(7); keyInfo.write("word159".getBytes(StandardCharsets.UTF_8));
            writeBigEndian(keyInfo, keyBlock.length, 4);
            writeBigEndian(keyInfo, keys.size(), 4);
            writeBigEndian(file, 1, 4);
            writeBigEndian(file, count, 4);
            writeBigEndian(file, keyInfo.size(), 4);
            writeBigEndian(file, keyBlock.length, 4);
            file.write(keyInfo.toByteArray()); file.write(keyBlock);
            byte[] record = new byte[recordSize];
            byte[] definition = "A large dictionary definition.".getBytes(StandardCharsets.UTF_8);
            System.arraycopy(definition, 0, record, 0, definition.length);
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            compressed.write(new byte[] {2, 0, 0, 0, 0, 0, 0, 0});
            try (DeflaterOutputStream zip = new DeflaterOutputStream(compressed)) { zip.write(record); }
            byte[] block = compressed.toByteArray();
            writeBigEndian(file, count, 4);
            writeBigEndian(file, count, 4);
            writeBigEndian(file, count * 8, 4);
            writeBigEndian(file, (long) count * block.length, 4);
            for (int i = 0; i < count; i++) {
                writeBigEndian(file, block.length, 4);
                writeBigEndian(file, recordSize, 4);
            }
            for (int i = 0; i < count; i++) file.write(block);
            return file.toByteArray();
        } catch (IOException error) {
            throw new FileNotFoundException(error.getMessage());
        }
    }

    private static byte[] rawBlock(byte[] contents) throws IOException {
        ByteArrayOutputStream block = new ByteArrayOutputStream();
        block.write(new byte[8]);
        block.write(contents);
        return block.toByteArray();
    }

    private static void writeBigEndian(ByteArrayOutputStream output, long value, int width) {
        for (int shift = (width - 1) * 8; shift >= 0; shift -= 8) {
            output.write((int) (value >>> shift) & 0xff);
        }
    }

    private static void writeLittleEndianInt(ByteArrayOutputStream output, int value) {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }
}
