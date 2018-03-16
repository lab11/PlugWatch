# OINK Firebase Functions #
This is the folder for the Firebase deployment of OINK.
In order to allow programmers to implement simultaneously firebase functions without conflict, we separated the functions from  the index.js files and created independent javaScript files for each function. All the functions need to have the following name format: **function_name.f.js** which will be parsed by index.js.


The index.js file automatically generates the name of the functions to be deployed an takes the path to the function file as name of the function in Firebase console.  For example, the structure of the file system is like this:
```
firebase/
    readme.md
    commitdeploy.sh 
    firebase_functions/ 
        firebase.json    
        functions/   
            index.js      
            oink/      
                core1.f.js  
            invites/     
                stimulus.f.js 
            payment/      
                apicallback.f.js
                korba.f.js
            ...
```

The key idea is that every incentive system should have a folder inside functions, which contains the functions directly related to that particular system. When the functions are deployed, they will have a name in camelcase format (*folderFunctionname*). For example the function inside the *invites* folder will be named **invitesStimulus** in the firebase console. Please make sure all the functions end with .f.js extension.

For more details, please visit: https://github.com/firebase/functions-samples/issues/170

## Committing and Deploying functions ##

We will be using a bash script that commits first and then deploy the specific function. This bash file is called commitdeploy.sh and is included in the project under firebase/ folder. For commiting and deployment use the following command in the terminal inside the function-specific folder:

`./commitdeploy.sh <function_name>.f.js functionNameInConsole "message for the commit"`

Please note that the second argument is in camel format as it should be deployed in the console of Firebase.

**Remember, index.js never changes and all the modules should be imported inside the function file NOT in index.js. This helps us to avoid conflicts when we commit.**

    
