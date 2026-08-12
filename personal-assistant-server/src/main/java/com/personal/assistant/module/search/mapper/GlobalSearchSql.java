package com.personal.assistant.module.search.mapper;

public final class GlobalSearchSql {
    private GlobalSearchSql() {}

    public static String sql() {
        return "select * from ("
                + "select 'TASK' type,id,title,coalesce(remark,'') snippet,created_at occurred_at,case when item_type='LIFE' then '/life' else '/work' end route from task_item where user_id=#{uid} and to_tsvector('simple',coalesce(title,'')||' '||coalesce(tags,'')||' '||coalesce(remark,''))@@plainto_tsquery('simple',#{keyword}) "
                + "union all select 'DEVLOG',id,title,coalesce(core_changes,''),occurred_at,'/devlogs' from dev_log where user_id=#{uid} and to_tsvector('simple',coalesce(title,'')||' '||coalesce(task_goal,'')||' '||coalesce(core_changes,'')||' '||coalesce(tags,''))@@plainto_tsquery('simple',#{keyword}) "
                + "union all select 'FINANCE',id,coalesce(merchant,'账单交易'),coalesce(description,''),transaction_time,'/finance' from finance_transaction where user_id=#{uid} and to_tsvector('simple',coalesce(merchant,'')||' '||coalesce(description,'')||' '||coalesce(note,''))@@plainto_tsquery('simple',#{keyword}) "
                + "union all select 'QUICK_NOTE',id,left(content,100),content,created_at,'/dashboard' from quick_note where user_id=#{uid} and content ilike '%'||#{keyword}||'%' "
                + "union all select 'LEARNING',id,coalesce(summary_type,'学习总结'),coalesce(markdown_content,''),created_at,'/learning/summaries' from learning_summary where user_id=#{uid} and (coalesce(markdown_content,'')||' '||coalesce(tags,'')) ilike '%'||#{keyword}||'%' "
                + "union all select 'STOCK',id,stock_name,coalesce(reason,'')||' '||coalesce(remark,''),created_at,'/stocks' from stock_watch_item where user_id=#{uid} and (stock_name||' '||stock_code||' '||coalesce(reason,'')||' '||coalesce(remark,'')) ilike '%'||#{keyword}||'%' "
                + "union all select 'TRADING_REVIEW',id,trade_date::text||' '||coalesce(market_stage,'复盘'),coalesce(auto_conclusion,'')||' '||coalesce(manual_judgment,''),created_at,'/trading-reviews' from trading_daily_review where user_id=#{uid} and (coalesce(auto_conclusion,'')||' '||coalesce(manual_judgment,'')||' '||coalesce(sectors,'')||' '||coalesce(core_stocks,'')) ilike '%'||#{keyword}||'%' "
                + "union all select 'TRADE',id,stock_name||' '||stock_code,coalesce(buy_logic,'')||' '||coalesce(sell_logic,'')||' '||coalesce(notes,''),created_at,'/trading-reviews' from trading_log where user_id=#{uid} and (stock_name||' '||stock_code||' '||coalesce(buy_logic,'')||' '||coalesce(sell_logic,'')||' '||coalesce(notes,'')) ilike '%'||#{keyword}||'%'"
                + ") search_results order by "
                + "case when lower(title)=lower(#{keyword}) then 3 when lower(title) like lower(#{keyword})||'%' then 2 when lower(title) like '%'||lower(#{keyword})||'%' then 1 else 0 end desc, "
                + "case when lower(snippet) like '%'||lower(#{keyword})||'%' then 1 else 0 end desc, occurred_at desc limit 100";
    }
}
