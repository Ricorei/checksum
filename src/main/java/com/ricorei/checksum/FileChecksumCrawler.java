package com.ricorei.checksum;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @author Ricorei
 */
public final class FileChecksumCrawler extends SimpleFileVisitor<Path> implements Iterable<Map.Entry<Path, FileChecksum>>
{
	private final HashMap<Path, FileChecksum> checksumMap;

	private String hashAlgorithm;

	public FileChecksumCrawler()
	{
		this("SHA-256");
	}

	private FileChecksumCrawler(String algorithm)
	{
		this.hashAlgorithm = Objects.requireNonNull(algorithm);
		this.checksumMap = new HashMap<>();
	}

	private static Optional<FileChecksum> getFileHash(String hashAlgorithm, Path path)
	{
		FileChecksum fileChecksum = null;

		try
		{
			MessageDigest md = MessageDigest.getInstance(hashAlgorithm);
			byte[] bytes = Files.readAllBytes(path);
			md.update(bytes);
			byte[] shaDig = md.digest();
			String hash = Base64.getEncoder().encodeToString(shaDig);

			fileChecksum = new FileChecksum(bytes.length, hash);

		}
		catch(NoSuchAlgorithmException | IOException e)
		{
			e.printStackTrace();
		}

		return Optional.ofNullable(fileChecksum);
	}

	public void importFromFile(Path fileName)
	{
		Objects.requireNonNull(fileName);

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
		Objects.requireNonNull(fileName);

		try(BufferedWriter bw = Files.newBufferedWriter(fileName, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
		{
			Set<Map.Entry<Path, FileChecksum>> set = this.checksumMap.entrySet();

			bw.write(this.hashAlgorithm);
			bw.newLine();
			bw.write(String.valueOf(set.size()));
			bw.newLine();
			for(Map.Entry<Path, FileChecksum> entry : set)
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

	/**
	 * Returns an exclusive list of duplicate ( by checksum ) files.<br/>
	 * If A and B are identical it will include A or B but not both.<br/>
	 * If C has no duplicate, C is not included.<br/>
	 *
	 * @return an exclusive list of duplicate ( by checksum ) files
	 */
	public FileChecksumCrawler getDuplicate()
	{
		FileChecksumCrawler duplicate = new FileChecksumCrawler(this.hashAlgorithm);
		HashSet<FileChecksum> processedChecksum = new HashSet<>();

		this.checksumMap.forEach(((path, fileChecksum) ->
		{
			if( !processedChecksum.add(fileChecksum) )
			{
				duplicate.checksumMap.put(path, fileChecksum);
			}
		}));

		return duplicate;
	}

	/**
	 * Returns an exclusive list of distinct ( by checksum ) files.<br/>
	 * If A and B are identical it will include A or B but not both.<br/>
	 * If C has no duplicate, C is included.<br/>
	 *
	 * @return an exclusive list of distinct ( by checksum ) files
	 */
	public FileChecksumCrawler getDistinct()
	{
		FileChecksumCrawler distinct = new FileChecksumCrawler(this.hashAlgorithm);
		HashSet<FileChecksum> processedChecksum = new HashSet<>();

		this.checksumMap.forEach(((path, fileChecksum) ->
		{
			if( processedChecksum.add(fileChecksum) )
			{
				distinct.checksumMap.put(path, fileChecksum);
			}
		}));

		return distinct;
	}

	/**
	 * Returns an exclusive list of distinct ( by checksum ) files.<br/>
	 * If A and B are identical it will include A or B but not both.<br/>
	 * If C has no duplicate, C is included.<br/>
	 * If D is in the specified set, D is ignored<br/>
	 *
	 * @param checksumToRemove
	 * @return an exclusive list of distinct ( by checksum ) files
	 */
	public FileChecksumCrawler getDistinct(FileChecksumCrawler checksumToRemove)
	{
		HashMap<FileChecksum, Path> retains = new HashMap<>();

		// retains only first occurrences
		this.checksumMap.forEach((path, fileChecksum) -> retains.putIfAbsent(fileChecksum, path));

		// remove all occurrences from the specified map
		checksumToRemove.checksumMap.forEach((path, fileChecksum) ->
		{
			if( retains.containsKey(fileChecksum) )
			{
				retains.remove(fileChecksum);
			}
		});

		FileChecksumCrawler distinct = new FileChecksumCrawler(this.hashAlgorithm);
		retains.forEach((fileChecksum, path) -> distinct.checksumMap.put(path, fileChecksum));
		return distinct;
	}

	@Override
	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
	{
		if( Files.isDirectory(file) )
		{
			return FileVisitResult.CONTINUE;
		}

		Optional<FileChecksum> fileHashOptional = getFileHash(this.hashAlgorithm, file);
		fileHashOptional.ifPresent(fileChecksum ->
		{
			this.checksumMap.put(file, fileChecksum);
		});

		return FileVisitResult.CONTINUE;
	}

	@Override
	public Iterator<Map.Entry<Path, FileChecksum>> iterator()
	{
		return this.checksumMap.entrySet().iterator();
	}

}
