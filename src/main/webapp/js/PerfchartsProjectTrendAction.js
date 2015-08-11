(function($){
    $(function(){
        var $builds = $("#builds");
        var $btn_submit = $("#btn_submit");
        var $message = $("#message");
        $btn_submit.click(function() {
            //alert($builds.val());
            $.post("submit", { builds : $builds.val() }, function(data, status, jqXHR){
                if (status !== "success" || data.error !== 0){
            			$message.addClass("error_msg_error");
            			$message.text("Failed");
            			return;
                }
            	location.href = data.url;
            }).fail(function() {
            			$message.addClass("error_msg_error");
            			$message.text("Failed.");
            });
        }); // end of $btn_submit.click()
    }); // end of $()
})(jQuery);