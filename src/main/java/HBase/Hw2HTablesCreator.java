package HBase;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.storm.json.simple.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static Utils.CommonConstants.*;


public class Hw2HTablesCreator {

    private static Admin admin;
    private static Connection conn;

    private static TableName lift_table_name = TableName.valueOf(LIFT_TABLE_NAME);
    private static TableName users_table_name = TableName.valueOf(USERS_TABLE_NAME);


    public static void createTables() throws IOException {

        // Instantiating configuration object
        Configuration conf = HBaseConfiguration.create();
        // Instantiating Connection object
        conn = ConnectionFactory.createConnection(conf);
        // Instantiating Admin object
        admin = conn.getAdmin();

        // delete lift and users tables if already exist
        if (admin.tableExists(lift_table_name))
        {
            admin.disableTable(lift_table_name);
            admin.deleteTable(lift_table_name);
        }
        if (admin.tableExists(users_table_name))
        {
            admin.disableTable(users_table_name);
            admin.deleteTable(users_table_name);
        }
        // create lift and users tables
        createLiftTable();
        createUsersTable();
    }

    public static Table getTable(TableName table_name) throws IOException {
        return conn.getTable(lift_table_name);
    }

    public static void cleanup() throws IOException {
        admin.close();
        conn.close();
    }

    private static void createLiftTable() throws IOException {

        // read pos/neg lift csv files
        BufferedReader in_pos = new BufferedReader(new FileReader(POS_LIFT_PATH));
        BufferedReader in_neg = new BufferedReader(new FileReader(NEG_LIFT_PATH));
        Iterable<CSVRecord> pos_records = CSVFormat.DEFAULT.parse(in_pos);
        Iterable<CSVRecord> neg_records = CSVFormat.DEFAULT.parse(in_neg);

        // create a map (x, pos/neg) -> [(y1, pos/neg lift), ..., (yk, pos/neg lift)]
        HashMap<JSONObject,JSONObject> liftMap = new HashMap<>();
        updateLiftMapFromCSV(liftMap, pos_records, POS);
        updateLiftMapFromCSV(liftMap, neg_records, NEG);

        // create the lift table resource with column family - lift
        HTableDescriptor tableDescriptor = new HTableDescriptor(lift_table_name);
        tableDescriptor.addFamily(new HColumnDescriptor(LIFT_TABLE_CF_LIFT));
        admin.createTable(tableDescriptor);

        Table table = conn.getTable(lift_table_name);

        // add lift data to the table
        List<Put> put_list = new ArrayList<>();

        for (Map.Entry<JSONObject, JSONObject> entry : liftMap.entrySet()) {
            // create a Put object with row key - (x, pos/neg)
            Put put = new Put(Bytes.toBytes(entry.getKey().toString()));
            put.addColumn(Bytes.toBytes(LIFT_TABLE_CF_LIFT), Bytes.toBytes(LIFT_TABLE_CF_LIFT_COL_TOP_K), Bytes.toBytes((entry.getValue()).toJSONString()));
            put_list.add(put);
//            table.put(put);
        }
        table.put(put_list);

        table.close();
    }

    private static void updateLiftMapFromCSV(HashMap<JSONObject,JSONObject> liftMap, Iterable<CSVRecord> records, String posOrNeg)
    {
        for (CSVRecord record : records) {
            String x = record.get(0);
            String y = record.get(1);
            String lift = record.get(2);

            // create a json object representing a key in lift map
            JSONObject key = new JSONObject();
            key.put(x, posOrNeg);

            // create/get the respected lift array (json object)
            JSONObject values;
            if (!liftMap.containsKey(key))
            {
                values = new JSONObject();
                liftMap.put(key, values);
            }
            values = liftMap.get(key);
            // add the new tuple (y, lift) to the respected lift array (json object)
            values.put(y, lift);
        }
    }

    private static void createUsersTable() throws IOException {
        // create the users table resource with column family - window
        HTableDescriptor tableDescriptor = new HTableDescriptor(users_table_name);
        tableDescriptor.addFamily(new HColumnDescriptor(USERS_TABLE_CF_WINDOW));
        admin.createTable(tableDescriptor);
    }

}
