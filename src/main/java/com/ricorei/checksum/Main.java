package com.ricorei.checksum;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.function.BiConsumer;

import static java.util.Objects.requireNonNull;

/**
 * @author Ricorei
 */
public final class Main
{
	private static final String FILE_EXT = ".fsum";
	private final Commands commands;

	private static final class Command
	{
		private final String name;
		private List<String> options;
		private String shortMan;

		public Command(String commandName)
		{
			this.name = requireNonNull(commandName);
			this.options = Collections.emptyList();
			this.shortMan = "";
		}

		public void setOptions(List<String> opts)
		{
			this.options = requireNonNull(opts);
		}

		public void setShortMan(String man)
		{
			this.shortMan = this.name + " " + requireNonNull(man);
		}

		public String getShortMan()
		{
			return this.shortMan;
		}

		public String getName()
		{
			return this.name;
		}

		public boolean containsOption(String option)
		{
			return this.options.contains(option);
		}

		public boolean hasOptions()
		{
			return !this.options.isEmpty();
		}

	}

	private static final class Commands
	{
		private final HashMap<String, Command> commandMans;
		private final HashMap<String, BiConsumer<Command, String[]>> runnables;

		public Commands()
		{
			this.commandMans = new HashMap<>();
			this.runnables = new HashMap<>();
		}

		public void add(Command command, BiConsumer<Command, String[]> commandRunnable)
		{
			requireNonNull(command);
			requireNonNull(commandRunnable);
			this.commandMans.put(command.getName(), command);
			this.runnables.put(command.getName(), commandRunnable);
		}

		private boolean contains(String command)
		{
			return this.commandMans.containsKey(command);
		}

		private boolean containsOption(String command, String option)
		{
			return contains(command) && this.commandMans.get(command).containsOption(option);
		}

		private boolean hasOptions(String command)
		{
			return this.commandMans.get(command).hasOptions();
		}

		public void run(String commandName, String[] args)
		{
			requireNonNull(commandName);
			requireNonNull(args);
			BiConsumer<Command, String[]> runnable = this.runnables.getOrDefault(commandName,
				(command, commandArgs) -> System.out.println("This command does nothing"));

			runnable.accept(this.commandMans.get(commandName), args);
		}
	}

	private Main()
	{
		this.commands = new Commands();

		Command extractCommand = new Command(ActionCommand.COMMAND_NAME);
		extractCommand.setOptions(Arrays.asList("--distinct", "--duplicate", "--unique", "--delete"));
		extractCommand.setShortMan(
			"[--distinct|--duplicate|--unique] [workingFile]" + FILE_EXT + " [[againstFile]" + FILE_EXT + "]");

		Command indexCommand = new Command("index");
		indexCommand.setShortMan("path [[path2] ... [pathN]]");

		this.commands.add(extractCommand, ActionCommand::run);
		this.commands.add(indexCommand, IndexCommand::run);
	}

	private static final class IndexCommand
	{
		private IndexCommand()
		{

		}

		private static void run(Command command, String[] args)
		{
			for( int i = 1; i < args.length; i++ )
			{
				indexCommand(args[i]);
			}
		}

		private static void indexCommand(String workingPathName)
		{
			Path workingPath = Paths.get(workingPathName);

			if( !Files.isDirectory(workingPath) )
			{
				System.out.println(workingPathName + " is not a directory");
				return;
			}

			FileChecksumCrawler fileIndex = new FileChecksumCrawler();

			ProgressBar progressBar = new ProgressBar();

			System.out.println("Indexing " + workingPath.toString() + ". This may takes a while ...");
			fileIndex.walk(workingPath, progressBar::setTotalCompletionValue,
				(filePath, attrs) -> progressBar.displayProgress(attrs.size()));

			Path fileName = Paths.get(workingPath.getFileName() + FILE_EXT);

			System.out.println("Saving index in " + fileName.toString());

			fileIndex.exportToFile(fileName);
		}
	}

	private static final class ActionCommand
	{
		private static final String COMMAND_NAME = "action";

		private ActionCommand()
		{

		}

		private static void run(Command command, String[] args)
		{
			if( args.length != 3 && args.length != 4 )
			{
				System.out.println(command.getShortMan());
				return;
			}

			String option = args[1];

			if( !command.containsOption(option) )
			{
				System.out.println(command.getShortMan());
				return;
			}

			if( args.length == 3 )
			{
				extractCommand(option, args[2], args[2]);
			}

			if( args.length == 4 )
			{
				extractCommand(option, args[2], args[3]);
			}
		}

		private static void extractCommand(String option, String leftFilename, String rightFilename)
		{
			if( !isValidFile(leftFilename) )
			{
				return;
			}

			if( !isValidFile(rightFilename) )
			{
				return;
			}

			FileChecksumCrawler leftIndexer = new FileChecksumCrawler();
			FileChecksumCrawler rightIndexer = new FileChecksumCrawler();

			leftIndexer.importFromFile(Paths.get(leftFilename));
			rightIndexer.importFromFile(Paths.get(rightFilename));

			FileChecksumCrawler indexerToExport = null;

			String indexerToExportName = UUID.randomUUID().toString();

			switch(option)
			{
				case "--duplicate":

					if( leftFilename.equals(rightFilename) )
					{
						indexerToExport = leftIndexer.getDuplicate();
					}
					else
					{
						indexerToExport = leftIndexer.getDuplicate(rightIndexer);
					}

					indexerToExportName = removeExtension(leftFilename) + "-duplicate";
					break;
				case "--distinct":

					if( leftFilename.equals(rightFilename) )
					{
						indexerToExport = leftIndexer.getDistinct();
					}
					else
					{
						indexerToExport = leftIndexer.getDistinct(rightIndexer);
					}

					indexerToExportName = removeExtension(leftFilename) + "-distinct";
					break;
				case "--unique":

					// TODO implements unique diff
					if( leftFilename.equals(rightFilename) )
					{
						System.out.println("--unique is not implemented yet");
						//indexerToExport = leftIndexer.getUnique();
					}
					else
					{
						System.out.println("--unique is not implemented yet");
						//indexerToExport = leftIndexer.getUnique(rightIndexer);
					}

					indexerToExportName = removeExtension(leftFilename) + "-unique";
					break;
				case "--delete":

					String warningMessage = "You are going to delete files from %s listed inside %s. Are you sure ? (y/n)";
					System.out.println(String.format(warningMessage, leftFilename, rightFilename));

					try(Scanner sis = new Scanner(System.in))
					{
						if( sis.hasNext() )
						{
							String answer = sis.next();

							if( answer.equals("y") )
							{
								ProgressBar progressBar = new ProgressBar();
								progressBar.setTotalCompletionValue(rightIndexer.size());

								ArrayList<Path> errors = new ArrayList<>();
								ArrayList<Path> deleted = new ArrayList<>();

								leftIndexer.deleteFiles(rightIndexer, p ->
								{
									deleted.add(p);
									progressBar.displayProgress(1);
								}, p ->
								{

									errors.add(p);
									progressBar.displayProgress(1);
								});

								indexerToExportName = removeExtension(leftFilename) + "-delete";
								indexerToExport = leftIndexer;
							}
						}
					}

					break;
			}

			if( indexerToExport != null )
			{
				indexerToExport.exportToFile(Paths.get(indexerToExportName + FILE_EXT));
			}
		}
	}

	private static String removeExtension(String file)
	{
		int dotIndex = file.lastIndexOf(".");
		if( dotIndex == -1 )
		{
			return file;
		}

		return file.substring(0, dotIndex);
	}

	private static boolean isValidFile(String file)
	{
		if( file == null )
		{
			return false;
		}

		if( !file.endsWith(FILE_EXT) )
		{
			System.out.println(file + " must be a " + FILE_EXT + " file");
			return false;
		}

		if( !Files.exists(Paths.get(file)) )
		{
			System.out.println(file + " must be a " + FILE_EXT + " file");
			return false;
		}

		return true;
	}

	public static void main(String[] args)
	{
		Main main = new Main();

		if( args.length == 0 )
		{
			System.out.println("Missing command");
			return;
		}

		String commandName = "";

		if( args.length >= 1 )
		{
			commandName = args[0];
			if( !main.commands.contains(commandName) )
			{
				System.out.println(commandName + " is not a valid command");
				return;
			}
		}

		if( args.length >= 2 )
		{
			if( main.commands.hasOptions(commandName) )
			{
				String optionName = args[1];

				if( !main.commands.containsOption(commandName, optionName) )
				{
					System.out.println(optionName + " is not a option for the [" + commandName + "] command");
					return;
				}
			}
		}

		main.commands.run(commandName, args);
	}

	private static final class ProgressBar
	{
		private long maximum;
		private long currentProgress;
		private long iterations;
		private long oldIterations;

		public ProgressBar()
		{
			setTotalCompletionValue(0);
		}

		private int getPercentage(long progress)
		{
			return (int) (((double) progress / (double) this.maximum) * 100d);
		}

		private String getPercentageDisplayString(int percentage)
		{
			if( percentage == 0 )
			{
				return "";
			}

			if( percentage % 10 == 0 )
			{
				long diffIterations = this.iterations - this.oldIterations;
				this.oldIterations = this.iterations;
				return " " + percentage + "% over " + this.iterations + " iterations (+" + diffIterations + ")\n";
			}
			else
			{
				return ".";
			}
		}

		public void setTotalCompletionValue(long completionValue)
		{
			this.maximum = completionValue;
			this.currentProgress = 0;
			this.iterations = 0;
			this.oldIterations = 0;
		}

		public void displayProgress(long addedProgress)
		{
			this.iterations += 1;
			int oldPercentage = getPercentage(this.currentProgress);
			this.currentProgress += addedProgress;
			int currentPercentage = getPercentage(this.currentProgress);

			while( oldPercentage < currentPercentage )
			{
				oldPercentage += 1;
				System.out.print(getPercentageDisplayString(oldPercentage));
			}
		}
	}
}
