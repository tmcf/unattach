package app.unattach.controller;

import app.unattach.model.Email;
import app.unattach.model.GetEmailMetadataTask;
import app.unattach.model.ProcessEmailResult;
import app.unattach.model.ProcessSettings;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.SortedMap;

public interface Controller {
  void clearPreviousSearch();
  String createLabel(String name);
  void donate(String item, int amount, String currency);
  List<Email> getEmails();
  String getEmailAddress() throws IOException;
  SortedMap<String, String> getIdToLabel();
  String getFilenameSchema();
  DefaultArtifactVersion getLatestVersion();
  String getOrCreateRemovedLabelId();
  LongTask<ProcessEmailResult> getProcessTask(Email email, ProcessSettings processSettings);
  String getRemovedLabelId();
  String getSearchQuery();
  GetEmailMetadataTask getSearchTask(String query) throws IOException, InterruptedException;
  String getTargetDirectory();
  void openFile(File file);
  void openQueryLanguagePage();
  void openUnattachHomepage();
  void openTermsAndConditions();
  void openWebPage(String uriString);
  void setFilenameSchema(String filenameSchema);
  void saveRemovedLabelId(String removedLabelId);
  void saveSearchQuery(String query);
  void saveTargetDirectory(String path);
  String signIn() throws IOException, GeneralSecurityException;
  void signOut();
  boolean sendToServer(String contentDescription, String exceptionText, String userText);
  void subscribe(String emailAddress) throws Exception;
}
