package com.ricorei.checksum;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import static java.util.Objects.requireNonNull;

/**
 * Computes files checksum recursively for a given directory.<br/>
 * <br/>
 * Filtering is available with three distinctive options :
 * <p>
 * Distinct : one of every duplicate and all without duplicate.<br/>
 * Duplicate : one of every duplicate.<br/>
 * Unique : all without duplicate.<br/>
 * </p>
 *
 * @author Ricorei
 */
public final class FileChecksumCrawler implements Iterable<Map.Entry<Path, FileChecksum>>
{
	private final HashMap<Path, FileChecksum> checksumMap;

	private String hashAlgorithm;

	public FileChecksumCrawler()
	{
		this("SHA-256");
	}

	private FileChecksumCrawler(String algorithm)
	{
		this.hashAlgorithm = requireNonNull(algorithm);
		this.checksumMap = new HashMap<>();
	}

	private static Optional<FileChecksum> getFileHash(String hashAlgorithm, Path path)
	{
		FileChecksum fileChecksum = null;

		int kilobytes = 1024;
		byte[] buffer = new byte[8 * kilobytes];
		int read;
		int size = 0;

		try
		{
			MessageDigest messageDigest = MessageDigest.getInstance(hashAlgorithm);

			try(InputStream inputStream = Files.newInputStream(path);
				DigestInputStream digestInputStream = new DigestInputStream(inputStream, messageDigest))
			{

				read = digestInputStream.read(buffer);
				while( read != -1 )
				{
					size += read;
					read = digestInputStream.read(buffer);
				}

				byte[] shaDig = messageDigest.digest();
				String hash = Base64.getEncoder().encodeToString(shaDig);

				fileChecksum = new FileChecksum(size, hash);
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
		}
		catch(NoSuchAlgorithmException e)
		{
			e.printStackTrace();
		}

		return Optional.ofNullable(fileChecksum);
	}

	public void walk(Path path, LongConsumer totalSizeConsumer, BiConsumer<Path, BasicFileAttributes> progressConsumer)
	{
		requireNonNull(path);
		requireNonNull(totalSizeConsumer);
		requireNonNull(progressConsumer);

		try
		{
			long totalSize = Files.walk(path).filter(p -> p.toFile().isFile()).mapToLong(p -> p.toFile().length()).sum();

			totalSizeConsumer.accept(totalSize);

			this.checksumMap.clear();
			Files.walkFileTree(path, new SimpleFileVisitor<Path>()
			{
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
				{
					if( Files.isRegularFile(file) )
					{
						Optional<FileChecksum> fileHashOptional = getFileHash(FileChecksumCrawler.this.hashAlgorithm, file);
						fileHashOptional.ifPresent(fileChecksum -> FileChecksumCrawler.this.checksumMap.put(file, fileChecksum));
						progressConsumer.accept(file, attrs);
					}

					return FileVisitResult.CONTINUE;
				}
			});
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	public void deleteFiles(FileChecksumCrawler removeAll, Consumer<Path> removedConsumer, Consumer<Path> errorConsumer)
	{
		requireNonNull(removeAll);
		requireNonNull(removedConsumer);
		requireNonNull(errorConsumer);

		// avoid concurrent modification exception if this == specified
		HashSet<Path> pathToDelete = removeAll.getUniquePathSet();

		for( Path path : pathToDelete )
		{
			if( !this.checksumMap.containsKey(path) )
			{
				errorConsumer.accept(path);
				continue;
			}

			if( !Files.isRegularFile(path) )
			{
				errorConsumer.accept(path);
				continue;
			}

			this.checksumMap.remove(path);
			removedConsumer.accept(path);

			try
			{
				Files.delete(path);

			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	public void importFromFile(Path fileName)
	{
		requireNonNull(fileName);

		this.checksumMap.clear();

		try(BufferedReader br = Files.newBufferedReader(fileName))
		{
			this.hashAlgorithm = br.readLine();
			int count = Integer.valueOf(br.readLine());

			for( int i = 0; i < count; i++ )
			{
				Path path = Paths.get(br.readLine());
				String checksum = br.readLine();
				long fileSize = Long.valueOf(br.readLine());
				this.checksumMap.put(path, new FileChecksum(fileSize, checksum));
			}
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	public void exportToFile(Path fileName)
	{
		requireNonNull(fileName);

		try(BufferedWriter bw = Files.newBufferedWriter(fileName, StandardOpenOption.CREATE,
			StandardOpenOption.TRUNCATE_EXISTING))
		{
			Set<Map.Entry<Path, FileChecksum>> set = this.checksumMap.entrySet();

			bw.write(this.hashAlgorithm);
			bw.newLine();
			bw.write(String.valueOf(set.size()));
			bw.newLine();
			for( Map.Entry<Path, FileChecksum> entry : set )
			{
				bw.write(entry.getKey().toString());
				bw.newLine();
				bw.write(entry.getValue().getSha256sum());
				bw.newLine();
				bw.write(String.valueOf(entry.getValue().getFileSize()));
				bw.newLine();
			}
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	public int size()
	{
		return this.checksumMap.size();
	}

	private FileChecksumCrawler getDuplicate(HashSet<FileChecksum> excluded)
	{
		FileChecksumCrawler duplicate = new FileChecksumCrawler(this.hashAlgorithm);

		this.checksumMap.forEach(((path, fileChecksum) ->
		{
			if( !excluded.add(fileChecksum) )
			{
				duplicate.checksumMap.put(path, fileChecksum);
			}
		}));

		return duplicate;
	}

	private FileChecksumCrawler getDistinct(HashSet<FileChecksum> included)
	{
		FileChecksumCrawler distinct = new FileChecksumCrawler(this.hashAlgorithm);
		this.checksumMap.forEach(((path, fileChecksum) ->
		{
			if( included.add(fileChecksum) )
			{
				distinct.checksumMap.put(path, fileChecksum);
			}
		}));
		return distinct;
	}

	private HashSet<FileChecksum> getUniqueChecksumSet()
	{
		HashSet<FileChecksum> uniqueSet = new HashSet<>();
		this.checksumMap.forEach((path, checksum) -> uniqueSet.add(checksum));
		return uniqueSet;
	}

	private HashSet<Path> getUniquePathSet()
	{
		HashSet<Path> uniqueSet = new HashSet<>();
		this.checksumMap.forEach((path, checksum) -> uniqueSet.add(path));
		return uniqueSet;
	}

	/**
	 * Returns an exclusive list of duplicate ( by checksum ) files.<br/>
	 * <p>
	 * The returned list contains files matching those rules :<br/>
	 * <ul>
	 * <li>Only the second or more occurrences of every file are included.</li>
	 * </ul>
	 * </p>
	 *
	 * @return an exclusive list of duplicate ( by checksum ) files
	 */
	public FileChecksumCrawler getDuplicate()
	{
		return getDuplicate(new HashSet<>());
	}

	/**
	 * Returns an exclusive list of duplicate ( by checksum ) files.<br/>
	 * <p>
	 * The returned list contains files matching those rules :<br/>
	 * <ul>
	 * <li>Only the second or more occurrences of every file are included.</li>
	 * <li>All files from the specified set are considered as the first occurrence.</li>
	 * </ul>
	 * </p>
	 *
	 * @return an exclusive list of duplicate ( by checksum ) files
	 */
	public FileChecksumCrawler getDuplicate(FileChecksumCrawler checksumToRemove)
	{
		return getDuplicate(checksumToRemove.getUniqueChecksumSet());
	}

	/**
	 * Returns an exclusive list of distinct ( by checksum ) files.<br/>
	 * <p>
	 * The returned list contains files matching those rules :<br/>
	 * <ul>
	 * <li>Only the first occurrence of every file is included.</li>
	 * </ul>
	 * </p>
	 *
	 * @return an exclusive list of distinct ( by checksum ) files
	 */
	public FileChecksumCrawler getDistinct()
	{
		return getDistinct(new HashSet<>());
	}

	/**
	 * Returns an exclusive list of distinct ( by checksum ) files.<br/>
	 * <p>
	 * The returned list contains files matching those rules :<br/>
	 * <ul>
	 * <li>Only the first occurrence of every file is included.</li>
	 * <li>All files from the specified set are considered as the first occurrence.</li>
	 * </ul>
	 * </p>
	 *
	 * @return an exclusive list of distinct ( by checksum ) files
	 */
	public FileChecksumCrawler getDistinct(FileChecksumCrawler checksumToRemove)
	{
		return getDistinct(checksumToRemove.getUniqueChecksumSet());
	}

	@Override
	public Iterator<Map.Entry<Path, FileChecksum>> iterator()
	{
		return this.checksumMap.entrySet().iterator();
	}
}
