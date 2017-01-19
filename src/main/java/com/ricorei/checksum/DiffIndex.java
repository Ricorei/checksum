package com.ricorei.checksum;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * @author Ricorei
 */
public final class DiffIndex
{
	private DiffIndex()
	{

	}

	public static void main(String[] args) throws IOException
	{
		if( args.length < 2)
		{
			System.out.println("Not enough parameters");
			return;
		}

		String leftFilename = args[0];
		String rightFilename = args[1];

		if( !leftFilename.endsWith(".fsum"))
		{
			System.out.println(leftFilename + " must be a .fsum file");
			return;
		}

		if( !rightFilename.endsWith(".fsum"))
		{
			System.out.println(rightFilename + " must be a .fsum file");
			return;
		}

		if( !Files.exists(Paths.get(leftFilename)))
		{
			System.out.println(leftFilename + " must be a .fsum file");
			return;
		}

		if( !Files.exists(Paths.get(rightFilename)))
		{
			System.out.println(rightFilename + " must be a .fsum file");
			return;
		}


		FileChecksumCrawler leftIndexer = new FileChecksumCrawler();
		leftIndexer.importFromFile(Paths.get(leftFilename));

		FileChecksumCrawler rightIndexer = new FileChecksumCrawler();
		rightIndexer.importFromFile(Paths.get(rightFilename));

		FileChecksumCrawler diff = leftIndexer.getDistinct(rightIndexer);

		diff.exportToFile(Paths.get("diff.fsum"));
	}
}
