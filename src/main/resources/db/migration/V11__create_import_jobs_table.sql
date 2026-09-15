create table import_jobs (
    job_id varchar(36) primary key,
    status varchar(32) not null,
    source_path varchar(2048) not null,
    source_sha256 varchar(64),
    categories varchar(1000),
    from_date date,
    max_records integer,
    records_read bigint not null default 0,
    accepted bigint not null default 0,
    imported bigint not null default 0,
    updated bigint not null default 0,
    skipped bigint not null default 0,
    invalid_records bigint not null default 0,
    cancel_requested boolean not null default false,
    started_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    completed_at timestamp,
    last_error varchar(1000)
);

create index idx_import_jobs_started_at on import_jobs(started_at);
create index idx_import_jobs_status on import_jobs(status);
