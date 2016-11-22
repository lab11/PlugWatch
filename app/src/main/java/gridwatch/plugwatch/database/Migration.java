package gridwatch.plugwatch.database;/*
 * Copyright 2014 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.realm.DynamicRealm;
import io.realm.RealmMigration;
import io.realm.RealmObjectSchema;
import io.realm.RealmSchema;

/**
 * Example of migrating a Realm file from version 0 (initial version) to its last version (version 3).
 */
public class Migration implements RealmMigration {

    @Override
    public void migrate(final DynamicRealm realm, long oldVersion, long newVersion) {

        RealmSchema schema = realm.getSchema();
        RealmObjectSchema WitEnergyMeasurement = schema.get("WitEnergyMeasurement");

        if (oldVersion == 0) {
            oldVersion++;
        }
        else if (oldVersion == 4) {
            oldVersion++;
        }
        else if (oldVersion == 5) {

            oldVersion++;
        }
        else if (oldVersion == 6) {
            RealmObjectSchema GWDumpSchema = schema.create("GWDump")
                    .addField("mID", String.class)
                    .addField("mDump", String.class);

        }
    }
}