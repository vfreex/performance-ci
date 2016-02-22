(function($){
    $(function(){
        var $builds = $("#builds");
        var $btn_submit = $("#btn_submit");
        var $message = $("#message");
        $btn_submit.click(function() {
            //alert($builds.val());
            var reportWindow = window.open("", "_blank");
            $.post("submit", { builds : $builds.val() }, function(data, status, jqXHR){
                if (status !== "success" || data.error !== 0){
            			$message.addClass("error_msg_error");
            			$message.text("Failed");
            			reportWindow.close()
            			return;
                }
            	reportWindow.location.href = data.url;
            	$message.text("");
            }).fail(function() {
            			$message.addClass("error_msg_error");
            			$message.text("Failed.");
            			reportWindow.close()
            });
        }); // end of $btn_submit.click()
    }); // end of $()
})(jQuery);