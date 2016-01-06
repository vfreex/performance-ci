(function($){
    var $sel_build = $("#sel_build");
    $.post("performance-report/getDestBuilds", function(data, status, jqXHR) {
        if (status === "success" && data.error === 0){
            for (var i = 0; i< data.result.length; ++i) {
                var item = data.result[i];
                $("<option/>").text(item.text).attr("value", item.value).appendTo($sel_build);
            }
        }
    });
    $sel_build.change(function() {
    var selectedBuildNumber = $(this).prop("value");
    if (selectedBuildNumber)
        window.open("performance-report/comparisonReport/" + selectedBuildNumber + "/monoReport");
    });
})(jQuery);