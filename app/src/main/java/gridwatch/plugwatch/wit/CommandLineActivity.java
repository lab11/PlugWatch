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

import gridwatch.plugwatch.R;
import gridwatch.plugwatch.callbacks.RestartOnExceptionHandler;
import gridwatch.plugwatch.configs.AppConfig;
import gridwatch.plugwatch.configs.IntentConfig;
import gridwatch.plugwatch.logs.GroupIDWriter;
import gridwatch.plugwatch.logs.PhoneIDWriter;


public class CommandLineActivity extends Activity {
    private EditText mCmd;
    private Button mExe = null;
    private CheckBox misText = null;
    private ListView mListView;


    String[] TESTS = new String[]{
            "-1,uploadall",
            "-1,upload_specific:20161128",
            "-1,uploadaudio",
            "-1,uploadlogs",
            "-1,uploadlog_specific:gw_mac.log",
            "-1,deletelog_specific:gw_mac.log",
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
            Thread.setDefaultUncaughtExceptionHandler(new RestartOnExceptionHandler(this,
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

        PhoneIDWriter r = new PhoneIDWriter(getApplicationContext());
        GroupIDWriter g = new GroupIDWriter(getApplicationContext());


        mExe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(getBaseContext(), APIService.class);
                i.putExtra("msg", mCmd.getText().toString());
                i.putExtra(IntentConfig.INCOMING_API_PHONE_ID, r.get_last_value());
                i.putExtra(IntentConfig.INCOMING_API_COMMAND, mCmd.getText().toString());
                i.putExtra(IntentConfig.INCOMING_API_GROUP_ID, g.get_last_value());
                if (misText.isChecked()) {
                    i.putExtra(IntentConfig.IS_TEXT, IntentConfig.IS_TEXT);
                }
                startService(i);
            }
        });
    }

}
