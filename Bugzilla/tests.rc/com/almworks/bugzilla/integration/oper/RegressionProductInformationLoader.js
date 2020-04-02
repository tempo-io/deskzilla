var initialowners = new Array(69);
var last_initialowner;
var initialccs = new Array(69);
var components = new Array(69);
var comp_desc = new Array(69);
var flags = new Array(69);
    components[0] = "\x3cNew Report Request>";
    comp_desc[0] = "Generic Report request without a specific group";
    initialowners[0] = "mark.brown\x40greenskycredit.com";
    flags[0] = [];

    initialccs[0] = "cheryl.jones\x40greenskycredit.com";

    components[1] = "Accounting :: \x3cNew Report Request>";
    comp_desc[1] = "Used to capture new reporting requests for the Accounting group of reports.";
    initialowners[1] = "mark.brown\x40greenskycredit.com";
    flags[1] = [];

    initialccs[1] = "cheryl.jones\x40greenskycredit.com";

    components[2] = "Accounting :: All Transaction Report";
    comp_desc[2] = "The All Transaction Report for Accounting.";
    initialowners[2] = "mark.brown\x40greenskycredit.com";
    flags[2] = [];

    initialccs[2] = "cheryl.jones\x40greenskycredit.com";

    components[3] = "Accounting :: Balance Roll Forward";
    comp_desc[3] = "Balance Rollforward Report";
    initialowners[3] = "paul.delorme\x40greenskycredit.com";
    flags[3] = [];

    initialccs[3] = "bobby.pavao\x40greenskycredit.com";

    components[4] = "Accounting :: Daily Summary";
    comp_desc[4] = "Daily Summary Accounting report";
    initialowners[4] = "paul.delorme\x40greenskycredit.com";
    flags[4] = [];

    initialccs[4] = "bobby.pavao\x40greenskycredit.com";

    components[5] = "Accounting :: Funding Report";
    comp_desc[5] = "Funding Report for Accounting";
    initialowners[5] = "paul.delorme\x40greenskycredit.com";
    flags[5] = [];

    initialccs[5] = "bobby.pavao\x40greenskycredit.com";

    components[6] = "Accounting :: High Balance and Internal";
    comp_desc[6] = "High Balance and Internal Report";
    initialowners[6] = "paul.delorme\x40greenskycredit.com";
    flags[6] = [];

    initialccs[6] = "bobby.pavao\x40greenskycredit.com";

    components[7] = "Accounting :: Ownership Roll Forward";
    comp_desc[7] = "Roll Forward by Ownership";
    initialowners[7] = "cheryl.jones\x40greenskycredit.com";
    flags[7] = [];

    initialccs[7] = "mark.brown\x40greenskycredit.com";

    components[8] = "Accounting :: Portfolio Detail List";
    comp_desc[8] = "Portfolio Detail List";
    initialowners[8] = "mark.brown\x40greenskycredit.com";
    flags[8] = [];

    initialccs[8] = "cheryl.jones\x40greenskycredit.com";

    components[9] = "Accounting :: PTD Payment History";
    comp_desc[9] = "Payment History PTD";
    initialowners[9] = "mark.brown\x40greenskycredit.com";
    flags[9] = [];

    initialccs[9] = "cheryl.jones\x40greenskycredit.com";

    components[10] = "AdHoc";
    comp_desc[10] = "AdHoc Report Request";
    initialowners[10] = "mark.brown\x40greenskycredit.com";
    flags[10] = [];

    initialccs[10] = "cheryl.jones\x40greenskycredit.com";

    components[11] = "Administration :: SubscritionsList";
    comp_desc[11] = "Subscription Error Mointoring Report";
    initialowners[11] = "mark.brown\x40greenskycredit.com";
    flags[11] = [];

    initialccs[11] = "cheryl.jones\x40greenskycredit.com";

    components[12] = "Benjamin Moore :: \x3cNew Report Request>";
    comp_desc[12] = "New report request for the Benjamin Moore group";
    initialowners[12] = "paul.delorme\x40greenskycredit.com";
    flags[12] = [];

    initialccs[12] = "bobby.pavao\x40greenskycredit.com";

    components[13] = "Benjamin Moore :: BM Application Report";
    comp_desc[13] = "Benjamin Moore Application Report";
    initialowners[13] = "paul.delorme\x40greenskycredit.com";
    flags[13] = [];

    initialccs[13] = "bobby.pavao\x40greenskycredit.com";

    components[14] = "Benjamin Moore :: BM Performance Report";
    comp_desc[14] = "BM Performance Report";
    initialowners[14] = "paul.delorme\x40greenskycredit.com";
    flags[14] = [];

    initialccs[14] = "bobby.pavao\x40greenskycredit.com";

    components[15] = "Collections :: \x3cNew Report Request>";
    comp_desc[15] = "Request for a new Collections report";
    initialowners[15] = "paul.delorme\x40greenskycredit.com";
    flags[15] = [];

    initialccs[15] = "bobby.pavao\x40greenskycredit.com";

    components[16] = "Collections :: Accounts Removed";
    comp_desc[16] = "Accounts removed from Collections report";
    initialowners[16] = "paul.delorme\x40greenskycredit.com";
    flags[16] = [];

    initialccs[16] = "bobby.pavao\x40greenskycredit.com";

    components[17] = "Collections :: Collection Summary Detail";
    comp_desc[17] = "Collection Summary Detail";
    initialowners[17] = "mark.brown\x40greenskycredit.com";
    flags[17] = [];

    initialccs[17] = "cheryl.jones\x40greenskycredit.com";

    components[18] = "Collections :: Collection Trend";
    comp_desc[18] = "Collections Trend";
    initialowners[18] = "mark.brown\x40greenskycredit.com";
    flags[18] = [];

    initialccs[18] = "cheryl.jones\x40greenskycredit.com";

    components[19] = "Collections :: Daily Collection Report";
    comp_desc[19] = "Daily Collection Report";
    initialowners[19] = "mark.brown\x40greenskycredit.com";
    flags[19] = [];

    initialccs[19] = "cheryl.jones\x40greenskycredit.com";

    components[20] = "Collections :: First Pay Default";
    comp_desc[20] = "First Pay Default";
    initialowners[20] = "mark.brown\x40greenskycredit.com";
    flags[20] = [];

    initialccs[20] = "";

    components[21] = "Collections :: LHR Notes";
    comp_desc[21] = "LHR Notes Report";
    initialowners[21] = "mark.brown\x40greenskycredit.com";
    flags[21] = [];

    initialccs[21] = "cheryl.jones\x40greenskycredit.com";

    components[22] = "Collections :: Ownership Collection Summary";
    comp_desc[22] = "Collection Summary by Ownership";
    initialowners[22] = "cheryl.jones\x40greenskycredit.com";
    flags[22] = [];

    initialccs[22] = "mark.brown\x40greenskycredit.com";

    components[23] = "Credit Risk :: Application Decision Report";
    comp_desc[23] = "Daily Application Decision Report";
    initialowners[23] = "cheryl.jones\x40greenskycredit.com";
    flags[23] = [];

    initialccs[23] = "mark.brown\x40greenskycredit.com";

    components[24] = "Credit Risk :: Cardholder Overlimit Report By Program";
    comp_desc[24] = "Overlimit by Program";
    initialowners[24] = "cheryl.jones\x40greenskycredit.com";
    flags[24] = [];

    initialccs[24] = "mark.brown\x40greenskycredit.com";

    components[25] = "Credit Risk :: Daily Monitoring Report";
    comp_desc[25] = "Display of Collections Table";
    initialowners[25] = "cheryl.jones\x40greenskycredit.com";
    flags[25] = [];

    initialccs[25] = "mark.brown\x40greenskycredit.com";

    components[26] = "Credit Risk :: Daily Risk Review Report";
    comp_desc[26] = "Report showing suspicious txns";
    initialowners[26] = "cheryl.jones\x40greenskycredit.com";
    flags[26] = [];

    initialccs[26] = "";

    components[27] = "Credit Risk :: Risk Trend Report";
    comp_desc[27] = "Risk Trend Report";
    initialowners[27] = "mark.brown\x40greenskycredit.com";
    flags[27] = [];

    initialccs[27] = "cheryl.jones\x40greenskycredit.com";

    components[28] = "Data Sources :: \x3cNew Report Request>";
    comp_desc[28] = "Used to capture requests for New Reporting needs.";
    initialowners[28] = "paul.delorme\x40greenskycredit.com";
    flags[28] = [];

    initialccs[28] = "bobby.pavao\x40greenskycredit.com";

    components[29] = "Entire Portfolio :: \x3cNew Report Request>";
    comp_desc[29] = "Used to capture requests for new reports for the Entire Portfolio group";
    initialowners[29] = "paul.delorme\x40greenskycredit.com";
    flags[29] = [];

    initialccs[29] = "bobby.pavao\x40greenskycredit.com";

    components[30] = "Entire Portfolio :: All Card Report";
    comp_desc[30] = "All Card Report";
    initialowners[30] = "paul.delorme\x40greenskycredit.com";
    flags[30] = [];

    initialccs[30] = "bobby.pavao\x40greenskycredit.com";

    components[31] = "Entire Portfolio :: Application Report";
    comp_desc[31] = "Application Report";
    initialowners[31] = "paul.delorme\x40greenskycredit.com";
    flags[31] = [];

    initialccs[31] = "bobby.pavao\x40greenskycredit.com";

    components[32] = "Entire Portfolio :: Approval Letters";
    comp_desc[32] = "Report for production of Approval Letters";
    initialowners[32] = "mark.brown\x40greenskycredit.com";
    flags[32] = [];

    initialccs[32] = "cheryl.jones\x40greenskycredit.com";

    components[33] = "Entire Portfolio :: Charge Off Report";
    comp_desc[33] = "Listing of Charged Off Cards";
    initialowners[33] = "cheryl.jones\x40greenskycredit.com";
    flags[33] = [];

    initialccs[33] = "mark.brown\x40greenskycredit.com";

    components[34] = "Entire Portfolio :: Credit Request";
    comp_desc[34] = "Credit Request Report";
    initialowners[34] = "paul.delorme\x40greenskycredit.com";
    flags[34] = [];

    initialccs[34] = "bobby.pavao\x40greenskycredit.com";

    components[35] = "Entire Portfolio :: Credit Score Request";
    comp_desc[35] = "Credit Score Request Report";
    initialowners[35] = "paul.delorme\x40greenskycredit.com";
    flags[35] = [];

    initialccs[35] = "bobby.pavao\x40greenskycredit.com";

    components[36] = "Entire Portfolio :: Identifier-Promotion-Report";
    comp_desc[36] = "Identifier Promotion Report";
    initialowners[36] = "paul.delorme\x40greenskycredit.com";
    flags[36] = [];

    initialccs[36] = "bobby.pavao\x40greenskycredit.com";

    components[37] = "Entire Portfolio :: Note Report";
    comp_desc[37] = "Note Report";
    initialowners[37] = "paul.delorme\x40greenskycredit.com";
    flags[37] = [];

    initialccs[37] = "bobby.pavao\x40greenskycredit.com";

    components[38] = "Entire Portfolio :: Portfolio Stats Dashboard";
    comp_desc[38] = "Portfolio Stats Dashboard";
    initialowners[38] = "mark.brown\x40greenskycredit.com";
    flags[38] = [];

    initialccs[38] = "cheryl.jones\x40greenskycredit.com";

    components[39] = "GreenSky :: \x3cNew Report Request>";
    comp_desc[39] = "New report request for the GreenSky report group";
    initialowners[39] = "paul.delorme\x40greenskycredit.com";
    flags[39] = [];

    initialccs[39] = "bobby.pavao\x40greenskycredit.com";

    components[40] = "GreenSky :: GreenSky Application Report";
    comp_desc[40] = "GreenSky Application Report";
    initialowners[40] = "paul.delorme\x40greenskycredit.com";
    flags[40] = [];

    initialccs[40] = "bobby.pavao\x40greenskycredit.com";

    components[41] = "Mac Tools :: \x3cNew Report Request>";
    comp_desc[41] = "New Report Request for the Mac Tools Report Group";
    initialowners[41] = "paul.delorme\x40greenskycredit.com";
    flags[41] = [];

    initialccs[41] = "bobby.pavao\x40greenskycredit.com";

    components[42] = "Mac Tools :: Approval Letters";
    comp_desc[42] = "Mac Tools Approval Letters Report";
    initialowners[42] = "paul.delorme\x40greenskycredit.com";
    flags[42] = [];

    initialccs[42] = "bobby.pavao\x40greenskycredit.com";

    components[43] = "Mac Tools :: Decline Letters";
    comp_desc[43] = "Mac Tools Decline Letters report";
    initialowners[43] = "paul.delorme\x40greenskycredit.com";
    flags[43] = [];

    initialccs[43] = "bobby.pavao\x40greenskycredit.com";

    components[44] = "Mac Tools :: Mac Card Application Report";
    comp_desc[44] = "Mac Card Application Report";
    initialowners[44] = "paul.delorme\x40greenskycredit.com";
    flags[44] = [];

    initialccs[44] = "bobby.pavao\x40greenskycredit.com";

    components[45] = "Mac Tools :: Mac Funding Report";
    comp_desc[45] = "Mac Funding Report";
    initialowners[45] = "paul.delorme\x40greenskycredit.com";
    flags[45] = [];

    initialccs[45] = "bobby.pavao\x40greenskycredit.com";

    components[46] = "Mac Tools :: Mac Loss Forecast";
    comp_desc[46] = "Mac Loss Forecast Report";
    initialowners[46] = "paul.delorme\x40greenskycredit.com";
    flags[46] = [];

    initialccs[46] = "bobby.pavao\x40greenskycredit.com";

    components[47] = "Mac Tools :: Mac Opt Out";
    comp_desc[47] = "Mac Opt Out Report";
    initialowners[47] = "paul.delorme\x40greenskycredit.com";
    flags[47] = [];

    initialccs[47] = "bobby.pavao\x40greenskycredit.com";

    components[48] = "Mac Tools :: Mac Portfolio Stats Performance";
    comp_desc[48] = "Mac Portfolio Stats Report";
    initialowners[48] = "mark.brown\x40greenskycredit.com";
    flags[48] = [];

    initialccs[48] = "cheryl.jones\x40greenskycredit.com";

    components[49] = "Mac Tools :: Mac Tools Underwriting Tool";
    comp_desc[49] = "Mac Tools Underwriting Tool report";
    initialowners[49] = "paul.delorme\x40greenskycredit.com";
    flags[49] = [];

    initialccs[49] = "bobby.pavao\x40greenskycredit.com";

    components[50] = "Mac Tools :: Mac Underwriting Results";
    comp_desc[50] = "Mac Underwriting Results Report";
    initialowners[50] = "paul.delorme\x40greenskycredit.com";
    flags[50] = [];

    initialccs[50] = "bobby.pavao\x40greenskycredit.com";

    components[51] = "Mac Tools :: Open To Buy";
    comp_desc[51] = "Mac Report with Open To Buy Data";
    initialowners[51] = "mark.brown\x40greenskycredit.com";
    flags[51] = [];

    initialccs[51] = "paul.delorme\x40greenskycredit.com";

    components[52] = "PAB :: Collection Summary Detail";
    comp_desc[52] = "Collections Summary for PAB";
    initialowners[52] = "cheryl.jones\x40greenskycredit.com";
    flags[52] = [];

    initialccs[52] = "mark.brown\x40greenskycredit.com";

    components[53] = "PAB :: Collections Summary";
    comp_desc[53] = "Collections Summary for PAB";
    initialowners[53] = "cheryl.jones\x40greenskycredit.com";
    flags[53] = [];

    initialccs[53] = "mark.brown\x40greenskycredit.com";

    components[54] = "PAB :: Historical Balances";
    comp_desc[54] = "Historical Balances for PAB";
    initialowners[54] = "cheryl.jones\x40greenskycredit.com";
    flags[54] = [];

    initialccs[54] = "mark.brown\x40greenskycredit.com";

    components[55] = "PAB :: New Accounts";
    comp_desc[55] = "New Accounts";
    initialowners[55] = "mark.brown\x40greenskycredit.com";
    flags[55] = [];

    initialccs[55] = "cheryl.jones\x40greenskycredit.com";

    components[56] = "PAB :: PAB Overlimit Accounts";
    comp_desc[56] = "List of Overlimit accounts with Portfolio_ID of PAB";
    initialowners[56] = "cheryl.jones\x40greenskycredit.com";
    flags[56] = [];

    initialccs[56] = "";

    components[57] = "PAB :: Portfolio Summary";
    comp_desc[57] = "PAB Portfolio Summary";
    initialowners[57] = "cheryl.jones\x40greenskycredit.com";
    flags[57] = [];

    initialccs[57] = "mark.brown\x40greenskycredit.com";

    components[58] = "PAB :: Trial Balance Detail";
    comp_desc[58] = "Detail Trial Balance";
    initialowners[58] = "cheryl.jones\x40greenskycredit.com";
    flags[58] = [];

    initialccs[58] = "mark.brown\x40greenskycredit.com";

    components[59] = "Phone Reports :: \x3cNew Report Request>";
    comp_desc[59] = "Request for a new report in the Phone Reports group";
    initialowners[59] = "paul.delorme\x40greenskycredit.com";
    flags[59] = [];

    initialccs[59] = "bobby.pavao\x40greenskycredit.com";

    components[60] = "Phone Reports :: Avaya Source";
    comp_desc[60] = "Avaya Source Phone report";
    initialowners[60] = "paul.delorme\x40greenskycredit.com";
    flags[60] = [];

    initialccs[60] = "bobby.pavao\x40greenskycredit.com";

    components[61] = "Phone Reports :: Call Stat Report";
    comp_desc[61] = "Call Stat Report";
    initialowners[61] = "paul.delorme\x40greenskycredit.com";
    flags[61] = [];

    initialccs[61] = "bobby.pavao\x40greenskycredit.com";

    components[62] = "Reporting Server";
    comp_desc[62] = "Reporting Server";
    initialowners[62] = "mark.brown\x40greenskycredit.com";
    flags[62] = [];

    initialccs[62] = "cheryl.jones\x40greenskycredit.com";

    components[63] = "Security";
    comp_desc[63] = "Security Request for Report Server";
    initialowners[63] = "mark.brown\x40greenskycredit.com";
    flags[63] = [];

    initialccs[63] = "cheryl.jones\x40greenskycredit.com";

    components[64] = "Superior :: \x3cNew Report Request>";
    comp_desc[64] = "New Report Request for the Superior Group";
    initialowners[64] = "paul.delorme\x40greenskycredit.com";
    flags[64] = [];

    initialccs[64] = "bobby.pavao\x40greenskycredit.com";

    components[65] = "Superior :: Superior Balance";
    comp_desc[65] = "Superior Balance Report";
    initialowners[65] = "mark.brown\x40greenskycredit.com";
    flags[65] = [];

    initialccs[65] = "cheryl.jones\x40greenskycredit.com";

    components[66] = "Superior :: Superior FC Report";
    comp_desc[66] = "Superior Finance Charge Report";
    initialowners[66] = "paul.delorme\x40greenskycredit.com";
    flags[66] = [];

    initialccs[66] = "bobby.pavao\x40greenskycredit.com";

    components[67] = "The Home Depot :: Application Decision Summary MTD";
    comp_desc[67] = "Home Depot Summary of decisoned applications (authorized or declined)";
    initialowners[67] = "cheryl.jones\x40greenskycredit.com";
    flags[67] = [];

    initialccs[67] = "";

    components[68] = "The Home Depot :: Application Decision Summary YTD";
    comp_desc[68] = "Home depot summary of applications decisioned (authorized or declined) in a year";
    initialowners[68] = "cheryl.jones\x40greenskycredit.com";
    flags[68] = [];

    initialccs[68] = "";


function set_assign_to() {
    // Based on the selected component, fill the "Assign To:" field
    // with the default component owner, and the "QA Contact:" field
    // with the default QA Contact. It also selectively enables flags.
    var form = document.Create;
    var assigned_to = form.assigned_to.value;


    var index = -1;
    if (form.component.type == 'select-one') {
        index = form.component.selectedIndex;
    } else if (form.component.type == 'hidden') {
        // Assume there is only one component in the list
        index = 0;
    }
    if (index != -1) {
        var owner = initialowners[index];
        var component = components[index];
        if (assigned_to == last_initialowner
            || assigned_to == owner
            || assigned_to == '') {
            form.assigned_to.value = owner;
            last_initialowner = owner;
        }

        document.getElementById('initial_cc').innerHTML = initialccs[index];
        document.getElementById('comp_desc').innerHTML = comp_desc[index];


        // First, we disable all flags. Then we re-enable those
        // which are available for the selected component.
        var inputElements = document.getElementsByTagName("select");
        var inputElement, flagField;
        for ( var i=0 ; i<inputElements.length ; i++ ) {
            inputElement = inputElements.item(i);
            if (inputElement.name.search(/^flag_type-(\d+)$/) != -1) {
                var id = inputElement.name.replace(/^flag_type-(\d+)$/, "$1");
                inputElement.disabled = true;
                // Also disable the requestee field, if it exists.
                inputElement = document.getElementById("requestee_type-" + id);
                if (inputElement) inputElement.disabled = true;
            }
        }
        // Now enable flags available for the selected component.
        for (var i = 0; i < flags[index].length; i++) {
            flagField = document.getElementById("flag_type-" + flags[index][i]);
            // Do not enable flags the user cannot set nor request.
            if (flagField && flagField.options.length > 1) {
                flagField.disabled = false;
                // Re-enabling the requestee field depends on the status
                // of the flag.
                toggleRequesteeField(flagField, 1);
            }
        }
    }
}

function handleWantsAttachment(wants_attachment) {
    if (wants_attachment) {
        document.getElementById('attachment_false').style.display = 'none';
        document.getElementById('attachment_true').style.display = 'block';
    }
    else {
        document.getElementById('attachment_false').style.display = 'block';
        document.getElementById('attachment_true').style.display = 'none';
        clearAttachmentFields();
    }
}


TUI_alternates['expert_fields'] = 'Show Advanced Fields';
// Hide the Advanced Fields by default, unless the user has a cookie
// that specifies otherwise.
TUI_hide_default('expert_fields');
