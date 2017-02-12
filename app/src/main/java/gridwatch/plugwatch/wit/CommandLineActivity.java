package gridwatch.plugwatch.wit;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import net.grandcentrix.tray.AppPreferences;
import net.grandcentrix.tray.core.ItemNotFoundException;

import gridwatch.plugwatch.R;
import gridwatch.plugwatch.callbacks.RestartOnExceptionHandler;
import gridwatch.plugwatch.configs.AppConfig;
import gridwatch.plugwatch.configs.IntentConfig;
import gridwatch.plugwatch.configs.SettingsConfig;

import static gridwatch.plugwatch.wit.App.getContext;


public class CommandLineActivity extends Activity {
    private EditText mCmd;
    private Button mExe = null;
    private CheckBox misText = null;
    private ListView mListView;
    final AppPreferences appPreferences = new AppPreferences(getContext());



        String[] TESTS = new String[]{
                "-1,get_app_pref_str",
                "-1,write_to_pref:WIFI_UPLOAD:true",
                "-1,write_to_pref:CHECK_CP:true",
            "-1,getrealmsizes",
            "-1,getlogsizes",
            "-1,get_specific_log_size:pw_mac.log",
            "-1,get_audio_size",
            "-1,num_realms",
            "-1,uploadall",
            "-1,upload_specific:20161128",
            "-1,uploadaudio",
            "-1,uploadlogs",
            "-1,uploadlog_specific:pw_mac.log",
            "-1,deletelog_specific:pw_mac.log",
            "-1,deletedb_specific:20161128",
            "-1,deleteaudio_specific:1",
            "-1,ping",
            "-1,sendsms",
            "-1,report",
            "-1,free_space",
            "-1,stats",
            "-1,reboot",
            "-1,getversion",
            "-1,sim",
            "-1,topup:1234",
            "-1,checkairtime",
            "-1,checkinternet",
            "-1,topupairtime:1234",
            "-1,topupinternet:1234",
            "-1,nuke_audio"

    };





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (AppConfig.RESTART_ON_EXCEPTION) {
            Thread.setDefaultUncaughtExceptionHandler(new RestartOnExceptionHandler(getBaseContext(), getClass().getName(),
                    PlugWatchUIActivity.class));
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_command_line);
        mCmd = (EditText) findViewById(R.id.cmdText);
        mExe = (Button) findViewById(R.id.btn_exe);

        misText = (CheckBox) findViewById(R.id.isTextCheckBox);
        mListView = (ListView) findViewById(R.id.testsListView);
        mListView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, TESTS));
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                                    long arg3) {
                mCmd.setText((String) ((TextView) arg1).getText());
            }
        });

        String phone_id = "-1";
        String group_id = "-1";
        try {
            phone_id = appPreferences.getString(SettingsConfig.PHONE_ID);
            group_id = appPreferences.getString(SettingsConfig.GROUP_ID);
        } catch (ItemNotFoundException e) {
            e.printStackTrace();
        }

        String finalPhone_id = phone_id;
        String finalGroup_id = group_id;
        mExe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(getBaseContext(), APIService.class);
                i.putExtra("msg", mCmd.getText().toString());
                i.putExtra(IntentConfig.INCOMING_API_PHONE_ID, finalPhone_id);
                i.putExtra(IntentConfig.INCOMING_API_COMMAND, mCmd.getText().toString());
                i.putExtra(IntentConfig.INCOMING_API_GROUP_ID, finalGroup_id);
                if (misText.isChecked()) {
                    i.putExtra(IntentConfig.IS_TEXT, IntentConfig.IS_TEXT);
                }
                startService(i);
            }
        });
    }

}
