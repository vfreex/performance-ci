(function($){
    $(function(){
        var $builds = $("#builds");
        var $btn_submit = $("#btn_submit");
        var $message = $("#message");
        $btn_submit.click(function() {
            //alert($builds.val());
            $.post("submit", { builds : $builds.val() }, function(data, status, jqXHR){
                if (status !== "success" || data.error !== 0){
            			$tag_error_msg.addClass("error_msg_error");
            			$tag_error_msg.text("Failed");
            			return;
                }
            	location.href = data.url;
            }).fail(function() {
            			$tag_error_msg.addClass("error_msg_error");
            			$tag_error_msg.text("Failed.");
            });
        }); // end of $btn_submit.click()
    }); // end of $()
})(jQuery);