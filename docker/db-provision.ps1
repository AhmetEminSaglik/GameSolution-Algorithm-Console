<#
.SYNOPSIS
  Paylasimli Postgres'te bir projeye IZOLE rol + database acar.

.DESCRIPTION
  Model: her proje = kendi database'i + kendi rolu. Rol SADECE kendi
  database'ine baglanabilir (CONNECT PUBLIC'ten alinir), superuser degildir,
  baska database/rol olusturamaz. Farkli projeler birbirinin verisini goremez.
  Idempotent: tekrar calistirmak guvenli.

.EXAMPLE
  .\docker\db-provision.ps1 avukat
  .\docker\db-provision.ps1 avukat s3cret
  .\docker\db-provision.ps1 avukat s3cret -AdminUser dbadmin -Container dev-postgres

.NOTES
  Prod icin: -PgHost / -PgPort ve $env:PGADMIN_PASSWORD ver -> docker yerine
  dogrudan `psql` kullanir (bkz. postgre-prod.md).
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory)][string]$Name,
  [string]$Password,
  [string]$AdminUser = "pathexplorer",
  [string]$AdminDb   = "postgres",
  [string]$Container  = "dev-postgres",
  [string]$PgHost,
  [int]$PgPort = 5443
)

$ErrorActionPreference = "Stop"

if (-not $Password) { $Password = $Name }

if ($Name -notmatch '^[a-z_][a-z0-9_]*$') {
  throw "isim sadece [a-z0-9_] olmali (kucuk harfle/altcizgiyle baslar): $Name"
}

# psql calistirici: local -> docker exec, prod -> dogrudan psql.
# psql NOTICE'lari stderr'e yazar; PowerShell 5.1 bunlari hata sanip durdurmasin
# diye stderr'i birlestirip SADECE exit code'a bakiyoruz.
function Invoke-AdminSql {
  param([string]$Db, [string]$Sql)
  $Sql = "SET client_min_messages=warning;`n$Sql"
  $prev = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try {
    if ($PgHost) {
      $env:PGPASSWORD = $env:PGADMIN_PASSWORD
      $out = $Sql | & psql -v ON_ERROR_STOP=1 -h $PgHost -p $PgPort -U $AdminUser -d $Db -X -q -t -A 2>&1
    } else {
      $out = $Sql | & docker exec -i $Container psql -v ON_ERROR_STOP=1 -U $AdminUser -d $Db -X -q -t -A 2>&1
    }
    $code = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $prev
  }
  if ($code -ne 0) {
    $out | ForEach-Object { Write-Host $_ }
    throw "psql hata verdi (db=$Db, exit=$code)"
  }
  # NOTICE/uyari satirlarini ele, geri kalani (sorgu ciktisi) dondur
  $out | Where-Object { "$_" -notmatch '(?i)^\s*(NOTICE|WARNING|DETAIL|HINT|CONTEXT)[:\s]' } |
         ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' }
}

if ($PgHost) { Write-Host ">> hedef: ${PgHost}:${PgPort}  admin=$AdminUser" }
else         { Write-Host ">> hedef: docker container $Container  admin=$AdminUser" }
Write-Host ">> rol + database: $Name"

# 1) ROL (idempotent; var olan superuser rolu bozmaz)
Invoke-AdminSql -Db $AdminDb -Sql @"
DO `$`$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$Name') THEN
    CREATE ROLE $Name LOGIN PASSWORD '$Password'
      NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    RAISE NOTICE 'rol olusturuldu: $Name';
  ELSE
    RAISE NOTICE 'rol zaten var: $Name (parola/attribute degistirilmedi)';
  END IF;
END
`$`$;
"@

# 2) DATABASE (kosullu; CREATE DATABASE tx disinda)
$exists = Invoke-AdminSql -Db $AdminDb -Sql "SELECT 1 FROM pg_database WHERE datname = '$Name';"
if ("$exists".Trim() -ne "1") {
  Invoke-AdminSql -Db $AdminDb -Sql "CREATE DATABASE $Name OWNER $Name;"
  Write-Host ">> database olusturuldu: $Name"
} else {
  Write-Host ">> database zaten var: $Name"
}

# 3) IZOLASYON: bu database'e sadece kendi rolu baglanabilsin
Invoke-AdminSql -Db $AdminDb -Sql @"
REVOKE CONNECT ON DATABASE $Name FROM PUBLIC;
GRANT  CONNECT ON DATABASE $Name TO $Name;
ALTER DATABASE $Name OWNER TO $Name;
"@

# 4) public schema: sadece kendi rolu obje olustursun
Invoke-AdminSql -Db $Name -Sql @"
REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO $Name;
GRANT ALL ON SCHEMA public TO $Name;
"@

$h = if ($PgHost) { $PgHost } else { "localhost" }
$p = if ($PgHost) { $PgPort } else { 5443 }

@"

==================================================================
  HAZIR:  rol=$Name  database=$Name
==================================================================
  psql URI : postgresql://${Name}:${Password}@${h}:${p}/${Name}
  JDBC     : jdbc:postgresql://${h}:${p}/${Name}
  .env     : DATABASE_URL=postgresql://${Name}:${Password}@${h}:${p}/${Name}
             PGHOST=$h
             PGPORT=$p
             PGDATABASE=$Name
             PGUSER=$Name
             PGPASSWORD=$Password

  test      : docker exec -it $Container psql -U $Name -d $Name -c "\conninfo"
  sema yukle: docker exec -i $Container psql -U $Name -d $Name < yol\schema.sql
==================================================================
"@ | Write-Host
