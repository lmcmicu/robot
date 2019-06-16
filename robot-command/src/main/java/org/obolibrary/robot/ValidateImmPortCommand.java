package org.obolibrary.robot;

import com.github.jsonldjava.utils.JsonUtils;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.Writer;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidateImmPortCommand implements Command {
  /** Logger */
  private static final Logger logger = LoggerFactory.getLogger(ValidateImmPortCommand.class);

  /** Used to store the command-line options for the command. */
  private Options options;

  /** Constructor: Initialises the command with its various options. */
  public ValidateImmPortCommand() {
    Options o = CommandLineHelper.getCommonOptions();
    o.addOption("j", "input", true, "JSON file containing the data to validate");
    o.addOption("o", "output", true, "save results to file");
    options = o;
  }

  /**
   * Returns the name of the command
   *
   * @return name
   */
  public String getName() {
    return "validate-immport";
  }

  /**
   * Returns a brief description of the command.
   *
   * @return description
   */
  public String getDescription() {
    return "validate stuff in ImmPort";
  }

  /**
   * Returns the command-line usage for the command.
   *
   * @return usage
   */
  public String getUsage() {
    return "validate-immport --input <JSON> --output <file>";
  }

  /**
   * Returns the command-line options for the command.
   *
   * @return options
   */
  public Options getOptions() {
    return options;
  }

  /**
   * Handles the command-line and file operations for the ValidateImmPortOperation
   *
   * @param args strings to use as arguments
   */
  public void main(String[] args) {
    try {
      execute(null, args);
    } catch (Exception e) {
      CommandLineHelper.handleException(e);
    }
  }

  /**
   * Given an input state and command line arguments, report on the differences between ontologies,
   * if any, and return the state unchanged.
   *
   * @param state the state from the previous command, or null
   * @param args the command-line arguments
   * @return the input state, unchanged
   * @throws Exception on any problem
   */
  public CommandState execute(CommandState state, String[] args) throws Exception {
    CommandLine line = CommandLineHelper.getCommandLine(getUsage(), getOptions(), args);
    if (line == null) {
      return null;
    }

    if (state == null) {
      state = new CommandState();
    }

    Writer writer;
    String outputPath = CommandLineHelper.getOptionalValue(line, "output");
    if (outputPath != null) {
      writer = new FileWriter(outputPath);
    } else {
      writer = new PrintWriter(System.out);
    }

    writer.write("Executing execute() ...\n");
    File inputFile = new File(CommandLineHelper.getOptionalValue(line, "input"));
    // The type of this object will be a List, Map, String, Boolean,
    // Number or null depending on the root object in the file:
    Object jsonObject = JsonUtils.fromString(FileUtils.readFileToString(inputFile));
    boolean valid = ValidateImmPortOperation.validate(jsonObject, writer);
    if (valid) {
      writer.write("The input was valid!\n");
    } else {
      writer.write("Argh! The input was not valid!\n");
    }

    writer.flush();
    writer.close();
    return state;
  }
}