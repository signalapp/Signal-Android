package org.thoughtcrime.securesms.export;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.StorageUtil;
import org.thoughtcrime.securesms.util.Util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The ChatExportFragment include the management elements of the settings
 * for starting the conversation export process.
 *
 * @author  @anlaji
 * @version 2.5 add_export_chats_feature
 * @since   2021-09-26
 */


public class ChatExportFragment extends Fragment {

    private static final String TAG = ChatExportFragment.class.getSimpleName ();

    private static final String      RECIPIENT_ID      = "recipient_id";
    private static final String      FROM_CONVERSATION = "FROM_CONVERSATION";
    private static final short       RESULT_GALLERY    = 1;


    private        ChatExportViewModel     viewModel;
    private static long                    existingThread;



    private        boolean       includeHTMLViewer;
    private        boolean       includeAllMedia;
    private        Uri        treeUri;
    private static ChatExportZipUtil zip;


    @SuppressLint("LogTagInlined") @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate (R.layout.chat_export_fragment, container, false);
        assert getArguments () != null;

        Log.w(TAG, String.valueOf(getArguments ().size()));
        RecipientId rId = (RecipientId) getActivity().getIntent().getExtras().get(RECIPIENT_ID);
        if(rId!=null)
        Log.w(TAG, rId.toString());
        existingThread = DatabaseFactory.getThreadDatabase (this.getContext ()).getThreadIdIfExistsFor (rId);
        Log.w(TAG, String.valueOf(existingThread));
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel  = ViewModelProviders.of(requireActivity()).get(ChatExportViewModel.class);

        View         allMedia = view.findViewById (R.id.include_all_media);
        View         htmlViewer = view.findViewById (R.id.include_html_viewer);
        SwitchCompat allMediaSwitch = view.findViewById (R.id.include_all_media_switch);
        SwitchCompat htmlViewerSwitch = view.findViewById (R.id.include_html_viewer_switch);
        View         selectTimePeriod = view.findViewById (R.id.chat_export_select_time_period);
        TextView     selectedTimePeriod = view.findViewById (R.id.chat_export_selected_time_period);
        Button       exportButton = view.findViewById (R.id.chat_export_button);

        allMedia.setOnClickListener(v -> viewModel.toggleSelectAllMedia ());

        viewModel.getAllMedia().observe(getViewLifecycleOwner(), shouldIncludeAllMedia -> {
            if (shouldIncludeAllMedia != allMediaSwitch.isChecked()) {
                includeAllMedia = true;
                allMediaSwitch.setChecked(shouldIncludeAllMedia);
                includeAllMedia = false;
            }
        });

        allMediaSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!includeAllMedia) {
                viewModel.setAllMedia (isChecked);
            }
        });

        allMediaSwitch.setChecked(viewModel.getCurrentSelectionAllMedia ());

        htmlViewer.setOnClickListener(v -> viewModel.toggleSelectHTMLViewer ());
        viewModel.getViewer().observe(getViewLifecycleOwner(), shouldIncludeViewer -> {
            if (shouldIncludeViewer != htmlViewerSwitch.isChecked()) {
                includeHTMLViewer = true;
                htmlViewerSwitch.setChecked(shouldIncludeViewer);
                includeHTMLViewer = false;
            }
        });

        htmlViewerSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!includeHTMLViewer) {
                viewModel.setViewer (isChecked);
            }
        });

        htmlViewerSwitch.setChecked(viewModel.getCurrentSelectionViewer ());

        selectTimePeriod.setOnClickListener(unused -> Navigation.findNavController (view)
                .navigate (R.id.action_chatExportTimePicker));


        viewModel.getSelectedTimePeriod ().observe(getViewLifecycleOwner(), selectedTimePeriod::setText);


        exportButton.setOnClickListener (
                unused -> chooseSaveFileLocation ());
    }


    private void chooseSaveFileLocation() {
        new AlertDialog.Builder(this.getParentFragment ().requireContext())
                .setView(R.layout.chatexport_choose_location_dialog)
                .setCancelable(true)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.ChatExportDialog_choose_folder, (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= 23) {
                        dialog.dismiss();
                        if (isUserSelectionRequired (getContext ()))
                            openGallery();
                        else
                            allowPermissionForFile();
                    } else
                        if (Build.VERSION.SDK_INT >= 21)
                            openGallery();
                })
                .create()
                .show();

    }


    public boolean isUserSelectionRequired (@NonNull Context context) {
        return ContextCompat.checkSelfPermission(this.getActivity (), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this.getActivity (), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void allowPermissionForFile() {
        Permissions.with(this)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .ifNecessary()
                .withPermanentDenialDialog (this.getString (R.string.ChatExport_signal_needs_the_storage_permission_in_order_to_write_to_external_storage_but_it_has_been_permanently_denied))
                .onAnyDenied (() -> Toast.makeText (this.getContext (), R.string.ChatExport_unable_to_write_to_external_storage_without_permission, Toast.LENGTH_LONG).show ())
                .onAllGranted(this::chooseSaveFileLocation)
                .execute();
    }

    @RequiresApi(api = 21)
    @SuppressLint("IntentReset")
    private void openGallery() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION       |
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                this.startActivityForResult(Intent.createChooser(intent, "Select a directory"), RESULT_GALLERY);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this.requireContext(), R.string.ChatExport_no_file_picker_available, Toast.LENGTH_LONG)
                        .show();
            }
             } catch (Exception e) {
            e.printStackTrace();
        }

    }
    @SuppressLint("NewApi")
    @RequiresApi(api = 24)
    public void onActivityResult (int requestCode, int resultCode, Intent data) {
        if (requestCode == RESULT_GALLERY && resultCode == Activity.RESULT_OK && data != null) {
            if(data != null) {
                treeUri = data.getData ();
                String storagePath = StorageUtil.getDisplayPath (getContext (), treeUri);
                Log.w (TAG, "Path: " + storagePath);
            }
            Navigation.findNavController(requireView()).popBackStack();
            onCreateClicked (viewModel);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void onCreateClicked (ChatExportViewModel viewModel)  {
        Toast.makeText (getContext (), "Start processing chat data", Toast.LENGTH_SHORT).show ();
        Date from = null, until = null;
        if(viewModel.getDateFrom().getValue()!=null)
            from = viewModel.getDateFrom ().getValue ().get ();
        if(viewModel.getDateUntil().getValue()!=null)
            until = viewModel.getDateUntil ().getValue ().get ();
        ChatFormatter formatter = new ChatFormatter (requireContext (),
                existingThread,
                from,
                until);

        Permissions.with(this)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .ifNecessary()
                .onAllGranted(() -> {
                    try {
                        createZip (formatter, viewModel, treeUri);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
                .withPermanentDenialDialog(getString(R.string.ChatExporter_signal_requires_external_storage_permission_in_order_to_create_chat_export_zip_file))
                .execute();
    }


    void createZip (@NonNull ChatFormatter exp, ChatExportViewModel viewModel, Uri uri) {
        String result = exp.parseConversationToXML ();
        if (result.length () > 0) {
            Map<String, ChatFormatter.MediaRecord> selectedMedia = new HashMap<> ();
            HashMap<String, Uri> otherFiles = new HashMap<> ();
            if (viewModel.getCurrentSelectionAllMedia ()) {
                selectedMedia = exp.getAllMedia ();
                for (Map.Entry<String, Uri> e : exp.getOtherFiles ().entrySet ()) {
                    otherFiles.put (e.getKey (), e.getValue ());
                }
            }
            handleSaveMedia (uri, selectedMedia.values (), otherFiles, viewModel.getCurrentSelectionViewer (), result);
        } else
            Toast.makeText (getContext (), "No messages to export", Toast.LENGTH_SHORT).show ();
    }

    private void handleSaveMedia (
            Uri path, @NonNull Collection<ChatFormatter.MediaRecord> mediaRecords, HashMap<String, Uri> moreFiles,
            boolean currentSelectionViewer,
            String result) {

        final Context context = requireContext ();

        if (StorageUtil.canWriteToMediaStore ()) {
            performSaveToDisk (context, path, mediaRecords, moreFiles, currentSelectionViewer, result);
            return;
        }

        ChatExportZipUtil.showWarningDialog (context, (dialogInterface, which) -> Permissions.with (this)
                        .request (Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        .ifNecessary ()
                        .withPermanentDenialDialog (context.getString (R.string.ChatExport_signal_needs_the_storage_permission_in_order_to_write_to_external_storage_but_it_has_been_permanently_denied))
                        .onAnyDenied (() -> Toast.makeText (context, R.string.ChatExport_unable_to_write_to_external_storage_without_permission, Toast.LENGTH_LONG).show ())
                        .onAllGranted (() -> performSaveToDisk (context, path, mediaRecords, moreFiles, currentSelectionViewer, result))
                        .execute (),
                (mediaRecords.size () + moreFiles.size ()));
    }


    private static void performSaveToDisk (@NonNull Context context,
                                           Uri path, @NonNull Collection<ChatFormatter.MediaRecord> mediaRecords,
                                           HashMap<String, Uri> moreFiles,
                                           boolean hasViewer,
                                           String resultXML) {

        new AsyncTask<Void, Void, List<ChatExportZipUtil.Attachment>> ()
        {

            final List<ChatExportZipUtil.Attachment> attachments = new LinkedList<> ();

            @Override
            protected void onPreExecute() {
                super.onPreExecute ();
            }

            @Override
            protected List<ChatExportZipUtil.Attachment> doInBackground(Void... params) {
                if (!Util.isEmpty (mediaRecords))
                    for (ChatFormatter.MediaRecord mediaRecord : mediaRecords) {
                        assert mediaRecord.getAttachment () != null;
                        if (mediaRecord.getAttachment ().getUri () != null) {
                            attachments.add (new ChatExportZipUtil.Attachment (mediaRecord.getAttachment ().getUri (),
                                    mediaRecord.getContentType (),
                                    mediaRecord.getDate (),
                                    mediaRecord.getAttachment ().getSize ()));
                        }
                        if (isCancelled ()) break;
                    }
                if (!Util.isEmpty (moreFiles.entrySet ()))
                    for (Map.Entry<String, Uri> e : moreFiles.entrySet ())
                        if (e.getValue () != null) try {
                            if (Build.VERSION.SDK_INT >= 26) {
                                attachments.add (new ChatExportZipUtil.Attachment (e.getValue (),
                                        Files.probeContentType (Paths.get (e.getValue ().getPath ())),
                                        new Date ().getTime (),
                                        (new File (String.valueOf (e.getValue ()))).length ()));
                            }
                        } catch (IOException ioException) {
                            ioException.printStackTrace ();
                        }
                return attachments;
            }

            @Override
            protected void onPostExecute(List<ChatExportZipUtil.Attachment> attachments) {
                super.onPostExecute(attachments);
                try {
                    zip = new ChatExportZipUtil (context, path, attachments.size(), existingThread);
                    zip.startToExport (context, hasViewer, resultXML);
                } catch (IOException | NoExternalStorageException e) {
                    e.printStackTrace ();
                    Log.w(TAG, e);
                }
                try{
                    if (!Util.isEmpty(attachments))
                        zip.executeOnExecutor (THREAD_POOL_EXECUTOR, attachments.toArray (new ChatExportZipUtil.Attachment[0]));
                    else
                        zip.executeOnExecutor (THREAD_POOL_EXECUTOR, (ChatExportZipUtil.Attachment[])  null);
                }catch (IllegalStateException e) {
                    e.printStackTrace ();
                    Log.w(TAG, e);
                }

            }
            @Override
            protected void onCancelled() {
                super.onCancelled();
            }

        }.execute();
    }
}