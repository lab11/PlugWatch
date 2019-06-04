//will incentivize every user for complianceApp-30 for 4 Cedis
function complianceApp(args) {
    compliance_list = [];

    if(args.currently_active == true) {
        //calculate the number of days between deployment and now
        days = ((((Date.now() - args.pilot_survey_time)/1000)/3600)/24)
        compliances_to_issue = Math.floor(days/30);
        for(let i = 0; i < compliances_to_issue; i++) {
            var obj = {};
            obj.amount = 4;
            obj.incentive_type = 'complianceApp';
            obj.incentive_id = '1-' + ((i+1)*30).toString();
            compliance_list.push(obj);
        }
    }

    return compliance_list;
}

//will incentivize every user for complianceApp-30 for 4 Cedis
function compliancePowerwatch(args) {
    compliance_list = [];

    if(args.deployment_number) {
        //for each deployment calculate the incentives
        var rollover = 0
        for(var i = 0; i < args.deployment_number; i++) {
            //is there an end time?
            if(args.powerwatch_deployment_end_times && args.powerwatch_deployment_end_times.length > i) {
                days = rollover + ((((args.powerwatch_deployment_end_times[i] - args.powerwatch_deployment_start_times[i])/1000)/3600)/24)
                rollover = days % 30;
            } else {
                days = ((((Date.now() - args.powerwatch_deployment_start_times[i])/1000)/3600)/24)

                //add in the rollover since they restarted
                days += rollover;
            }

            compliances_to_issue = Math.floor(days/30);
            

            for(let j = 0; j< compliances_to_issue; j++) {
                var obj = {};
                obj.amount = 5;
                obj.incentive_type = 'compliancePowerwatch';
                obj.incentive_id = (i+1).toString() + '-' + ((j+1)*30).toString();
                compliance_list.push(obj);
            }
        }
    }

    return compliance_list;
}

module.exports = {
    complianceApp,
    compliancePowerwatch,
};
