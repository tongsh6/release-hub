create table run_item_metadata (
    run_item_id varchar(255) not null,
    metadata_key varchar(255) not null,
    metadata_value text,
    primary key (run_item_id, metadata_key),
    constraint fk_run_item_metadata_item foreign key (run_item_id) references run_item(id) on delete cascade
);
