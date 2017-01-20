package com.ricorei.checksum;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Ricorei
 */
public final class CreateDuplicateIndex
{
	private CreateDuplicateIndex()
	{

	}

	public static void main(String[] args) throws IOException
	{
		for(String workingPathName : args)
		{
			Path workingPath = Paths.get(workingPathName);

			if( !Files.isDirectory(workingPath) )
			{
				System.out.println(workingPathName + " is not a directory");
				return;
			}

			FileChecksumCrawler fileIndex = new FileChecksumCrawler();

			System.out.println("Indexing " + workingPath.toString() + ". This may takes a while ...");
			fileIndex.walk(workingPath, s -> {}, (p,a) -> {});
			fileIndex = fileIndex.getDuplicate();

			Path fileName = Paths.get(workingPath.getFileName() + ".fsum");

			System.out.println("Saving index in " + fileName.toString());

			fileIndex.exportToFile(fileName);
		}
	}
}
